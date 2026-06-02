/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.dcmnt.space;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.BaseQ;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.Tokens.SUBQ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.dcmnt.dcmntInstSet.DCMNT_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * dcmntSpaceSubQ - Subscription query for MongoDB/DocumentDB change streams
 *
 * <p>Provides real-time document change notifications using MongoDB's Change Streams feature.
 * Users can subscribe to document changes via:
 *
 * <pre>{@code
 * // Subscribe to all changes in a collection
 * mongo:users/+?subq -> |print("User changed: ${_}")
 *
 * // Subscribe to a specific document
 * mongo:users/507f1f77bcf86cd799439011?subq -> |print("Document changed: ${_}")
 *
 * // Unsubscribe
 * mongo:users/+?subq => ~
 * }</pre>
 *
 * <p>The subscription callback receives:
 * <ul>
 *   <li>For insert/update/replace: the full document as a rec</li>
 *   <li>For delete: noobj (triggers callback with noobj)</li>
 * </ul>
 *
 * <p><b>Note:</b> MongoDB Change Streams require a replica set or sharded cluster.
 * Standalone MongoDB instances do not support change streams.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class dcmntSpaceSubQ extends BaseQ {

    protected final dcmntSpace space;
    protected final QProc subq = QCollection.subq();

    // Track active change stream watchers: fURI pattern -> (cursor, running flag, future)
    private final Map<fURI, WatcherHandle> activeWatchers = new ConcurrentHashMap<>();

    private record WatcherHandle(MongoCursor<ChangeStreamDocument<Document>> cursor, AtomicBoolean running, Future<?> future) {
        void stop() {
            running.set(false);
            try {
                cursor.close();
            } catch (final Exception e) {
                // Ignore close errors
            }
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    public dcmntSpaceSubQ(final dcmntSpace space) {
        super(new HashMap<>(), DCMNT_SPACE_TID.extend("subq"), null);
        this.space = space;
        this.onWrite = new OnWrite();
        this.onRead = this.subq.onRead().get();
    }

    public class OnWrite extends BaseOnWrite {

        public OnWrite() {
            super(noobj(), noobj(), noobj());
        }

        @Override
        public Optional<Obj> qlessWrite(final fURI vid, final Obj obj) {
            // Delegate to subq's qlessWrite to trigger subscription callbacks
            return subq.onWrite().flatMap(w -> w.qlessWrite(vid, obj));
        }

        @Override
        public Optional<Obj> preWrite(final fURI vid, final Obj obj) {
            LOG.trace("evaluating {{y}}prewrite{{/y}}: %s => %s", obj, vid);

            if (vid.hasQ(SUBQ)) {
                final fURI basePath = vid.basePath();

                if (obj.isNoObj()) {
                    // Unsubscribe: stop the change stream watcher
                    final WatcherHandle handle = activeWatchers.remove(basePath);
                    if (handle != null) {
                        handle.stop();
                        LOG.info("unsubscribed from change stream: {{y}}%s{{X}}", basePath);
                    }
                    // Also remove from subq's subscription list
                    subq.onWrite().ifPresent(w -> w.preWrite(vid, obj));
                } else {
                    // Subscribe: start a change stream watcher
                    // First register with subq
                    subq.onWrite().ifPresent(w -> w.preWrite(vid, obj));

                    // Parse the path to determine collection and optional document ID
                    final fURI relativePath = stripPatternPrefix(basePath);
                    final List<String> segments = relativePath.segments();

                    if (segments.isEmpty()) {
                        LOG.warn("cannot subscribe to empty path: %s", basePath);
                        return Optional.empty();
                    }

                    final String collectionName = segments.getFirst();
                    final String documentId = segments.size() > 1 ? segments.get(1) : null;

                    // Don't create duplicate watchers for the same pattern
                    if (activeWatchers.containsKey(basePath)) {
                        LOG.debug("watcher already exists for: %s", basePath);
                        return Optional.empty();
                    }

                    try {
                        startChangeStreamWatcher(basePath, collectionName, documentId);
                        LOG.info("subscribed to change stream: {{y}}%s{{X}} (collection: %s, docId: %s)",
                                basePath, collectionName, documentId);
                    } catch (final Exception e) {
                        LOG.error("failed to start change stream for %s: %s", basePath, e.getMessage());
                        return Optional.empty();
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Start a background thread that watches a MongoDB change stream and dispatches events.
     */
    private void startChangeStreamWatcher(final fURI basePath, final String collectionName, final String documentId) {
        final MongoCollection<Document> collection = space.getDatabase().getCollection(collectionName);

        // Build the change stream pipeline
        final ChangeStreamIterable<Document> changeStream;

        if (documentId == null || documentId.equals("+") || documentId.equals("#")) {
            // Watch entire collection
            changeStream = collection.watch().fullDocument(FullDocument.UPDATE_LOOKUP);
        } else {
            // Watch specific document by _id
            final Object docIdFilter = parseObjectId(documentId);
            final List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(Filters.eq("documentKey._id", docIdFilter))
            );
            changeStream = collection.watch(pipeline).fullDocument(FullDocument.UPDATE_LOOKUP);
        }

        final MongoCursor<ChangeStreamDocument<Document>> cursor = changeStream.iterator();
        final AtomicBoolean running = new AtomicBoolean(true);

        final Future<?> future = BootLoader.getExecutor().submit(() -> {
            LOG.debug("change stream watcher started for: %s", basePath);
            try {
                while (running.get() && cursor.hasNext()) {
                    final ChangeStreamDocument<Document> change = cursor.next();
                    processChangeEvent(basePath, collectionName, change);
                }
            } catch (final Exception e) {
                if (running.get()) {
                    LOG.error("change stream error for %s: %s", basePath, e.getMessage());
                }
            } finally {
                running.set(false);
                try {
                    cursor.close();
                } catch (final Exception ignored) {
                }
                activeWatchers.remove(basePath);
                LOG.debug("change stream watcher stopped for: %s", basePath);
            }
        });

        activeWatchers.put(basePath, new WatcherHandle(cursor, running, future));
    }

    /**
     * Process a change stream event and dispatch to subscriptions.
     */
    private void processChangeEvent(final fURI subscriptionPath, final String collectionName,
                                     final ChangeStreamDocument<Document> change) {
        final OperationType opType = change.getOperationType();
        LOG.trace("change event: %s on %s", opType, collectionName);

        // Get the document ID
        final BsonDocument docKey = change.getDocumentKey();
        if (docKey == null) {
            LOG.debug("change event without document key, skipping");
            return;
        }

        final String docIdStr;
        if (docKey.containsKey("_id")) {
            final var idValue = docKey.get("_id");
            if (idValue.isObjectId()) {
                docIdStr = idValue.asObjectId().getValue().toHexString();
            } else if (idValue.isString()) {
                docIdStr = idValue.asString().getValue();
            } else {
                docIdStr = idValue.toString();
            }
        } else {
            LOG.debug("change event without _id in document key, skipping");
            return;
        }

        // Build the document fURI
        final fURI docVID = f(space.pattern().retractPattern().extend(collectionName).extend(docIdStr).toString());

        // Determine the obj to send to subscribers
        final Obj obj;
        if (opType == OperationType.DELETE) {
            obj = noobj();
        } else {
            // For insert, update, replace - get the full document
            final Document fullDoc = change.getFullDocument();
            if (fullDoc != null) {
                obj = space.getSerializer().readRec(fullDoc.toBsonDocument()).selfVID(docVID);
            } else {
                // Fallback if full document not available
                LOG.debug("full document not available for change event on %s", docVID);
                obj = noobj();
            }
        }

        // Dispatch to subscriptions via qlessWrite
        LOG.debug("dispatching change event to %s: %s => %s", subscriptionPath, docVID, opType);
        this.onWrite.qlessWrite(docVID, obj);
    }

    /**
     * Strip the space's route prefix from a fURI to get the relative path.
     */
    private fURI stripPatternPrefix(final fURI furi) {
        if (!space.routes().isEmpty()) {
            final studio.phaseshift.metatron.isa.m.type.Uri routeTarget = space.routes().values().iterator().next();
            final fURI prefix = routeTarget.asUri().uriValue().asNode();
            if (!prefix.path().isEmpty() && prefix.path().stream().anyMatch(s -> !s.isEmpty())) {
                return furi.removePrefix(prefix);
            }
        }
        final fURI patternBase = space.pattern().asNode();
        return furi.removePrefix(patternBase);
    }

    /**
     * Parse a string as an ObjectId, handling both hex strings and other formats.
     */
    private Object parseObjectId(final String id) {
        if (id == null) {
            return null;
        }
        if (id.matches("[0-9a-fA-F]{24}")) {
            return new ObjectId(id);
        }
        return id;
    }

    /**
     * Close all active change stream watchers.
     * Called when the space is closed.
     */
    public void closeAll() {
        LOG.info("closing all change stream watchers (%d active)", activeWatchers.size());
        activeWatchers.values().forEach(WatcherHandle::stop);
        activeWatchers.clear();
    }
}
