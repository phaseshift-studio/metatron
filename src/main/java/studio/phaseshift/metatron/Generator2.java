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
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.nio.file.Path;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Generator2 {

    protected static final GraphittyLogger LOG = Graphitty.log(Generator2.class);

    protected final String name;
    protected final File bootFile;
    protected final File skillFile;
    protected final boolean isSingleFile;

    public Generator2(final String bootFile) {
        LOG.none(CommonUtil.getHeader("conf/ansi_headers.txt", null, true));
        final fURI locationURI = f(bootFile);
        final int demarcate = locationURI.name().indexOf('.');
        this.name = -1 == demarcate ? locationURI.name() : locationURI.name().substring(0, demarcate);
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

    public void start() {
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
