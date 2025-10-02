package studio.phaseshift.metatron.space.fs;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Str;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.MStr;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.mem.MSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MStr.str;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

public class FileSpace extends MSpace {

    public static final fURI FILESPACE_TID = f("/mtron/io/fs");

    public FileSpace(final fURI pattern, final fURI vid) {
        super(pattern, FILESPACE_TID, vid);
    }

    @Override
    public Obj read(final fURI vid) {
        return Space.Helpers.resolveRead(this, vid, (key) -> {
            if (key.equals(fURI.ALL_WILD))
                throw MTronException.of("infinite nested walks on file system not allowed");
            else {
                if (key.hasPattern()) {
                    try (Stream<Path> walk = Files.walk(Path.of(this.pattern.retractPattern().toString()), vid.segments().size(), FileVisitOption.FOLLOW_LINKS)) {
                        return walk.map(p -> f(p.toString())).filter(p -> p.matches(vid)).collect(Collectors.toMap(p -> p, p -> uri(p.toString()), (a, b) -> b, LinkedHashMap::new));
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }
                } else {
                    try {
                        final Path vidPath = Path.of(vid.toString());
                        if (Files.isDirectory(vidPath)) {
                            return Files.list(vidPath)
                                    .collect(Collectors.toMap(
                                            p -> f(p.toString()),
                                            p -> MRec.ofUriKeyed("name", str(p.getFileName().toString()), "permissions", str(MTronException.wrap(() -> Files.getPosixFilePermissions(p)).toString())), (a, b) -> b, LinkedHashMap::new));
                        } else {
                            final Str value = MStr.of(Files.readString(vidPath));
                            return Map.of(vid, value);
                        }
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }

                }
            }
        });


    }

    @Override
    public Obj write(fURI vid, Obj obj) {
        return NoObj.single();
    }

    @Override
    public void append(fURI addr, Obj... obj) {

    }
}
