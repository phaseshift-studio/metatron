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

package studio.phaseshift.metatron;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Selector;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.TableWidget;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ_PATTERN;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Generator {

    protected static final GraphittyLogger LOG = Graphitty.log(Generator.class);

    protected final fURI locationURI;
    protected final String name;
    /// //////////////////////////////////
    protected File bootFile;
    protected File skillFile;
    protected boolean isSingleFile;

    public Generator(final String bootFile) {
        LOG.none(CommonUtil.getHeader("conf/ansi_headers.txt", null, true));
        this.locationURI = f(bootFile);
        final int demarcate = locationURI.name().indexOf('.');
        this.name = -1 == demarcate ? locationURI.name() : locationURI.name().substring(0, demarcate);

    }

    public void start() {
        final Console console = new Console(rec(uri(HEADER), str("")), f("/sys/console"));
        this.createPackageOrFile();
        this.createSpace();
        this.finish();
        console.close();
    }

    public void createPackageOrFile() {
        try {
            if (locationURI.name().contains(".")) {
                this.skillFile = null;
                this.isSingleFile = true;
                LOG.none("   generating metatron boot file: {{b}}%s\n", locationURI);
                new File(locationURI.retract(1).toString()).mkdirs();
                this.bootFile = Path.of(locationURI.toString()).toFile();
                this.bootFile.createNewFile();
                LOG.none("{{^<1}}{{g}}[O]{{X}}{{v<1}}");
            } else {
                LOG.none("   generating metatron package boot file: {{b}}%s\n", locationURI);
                new File(locationURI.toString()).mkdirs();
                new File(locationURI.extend("assets").toString()).mkdirs();
                this.bootFile = Path.of(locationURI.extend("assets").extend(locationURI.name() + ".boot.mtron").toString()).toFile();
                this.bootFile.createNewFile();
                LOG.none("{{^<1}}{{g}}[O]{{X}}{{v<1}}");
                LOG.none("   generating metatron package skill file: {{b}}%s\n", locationURI.extend("SKILL.md"));
                this.skillFile = Path.of(locationURI.extend("SKILL.md").toString()).toFile();
                this.skillFile.createNewFile();
                LOG.none("{{^<1}}{{g}}[O]{{X}}{{v<1}}");
                this.isSingleFile = false;
            }
        } catch (final Exception e) {
            LOG.none("{{^<1}}{{r}}[X]{{X}}{{v<1}}");
            throw MTronException.of(e);
        }
    }

    public void createSpace() {
        final memSpace globalSpace = memSpace.of(rec(uri(PATTERN), uri("#")), f("/sys/global"));
        final List<Type> spaceTypes = InstSet.loadInstSetProvider(f("#"))
                .flatMap(isa -> isa.get().types().stream())
                .filter(s -> s.asType().test(SPACE_TYPE) || s.asType().vid().toString().contains("space"))
                .toList();
        final Selector select = new Selector();
        final TableWidget spaceTable = (TableWidget) new TableWidget(List.of("", "space", "description"))
                .style()
                .border(Border.continuous.foreground("{{b}}"))
                .headerDivider("{{b}}" + Border.continuous.leftSide())
                .applyStyle();
        spaceTypes.forEach(s -> {
            spaceTable.addRow(List.of("[ ]", s.vid().name(), Router.readFromSpace(s.vid().addQ(DOCQ_PATTERN.toString())).orElse(rec()).at(DESC)));
        });
        System.out.println(spaceTypes);
        LOG.none(spaceTable.format());
        LOG.none("{{X}}\n");
        //Utilities.runCursorLessWidget(spaceTable, true);
        //spaceTable.run();
    }

    public void finish() {
        LOG.none("completed %s {{X}}generation\n", this.isSingleFile ? "{{y}}boot file" : "{{y}}boot package");
        if (!this.isSingleFile) {
            LOG.none("""
                     %s package structure
                     %s
                     |_ SKILL.md
                     |_ assets
                       \\_ %s
                     |_ resources
                       \\_ <none>{{X}}
                     """, this.name, this.skillFile.getParent(), this.bootFile.getName());
        }
    }
}
