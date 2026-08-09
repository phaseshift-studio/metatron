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

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.ServerSocket;
import java.util.Optional;

/*
 * @SkipWhenPortUnavailable(value = 8080)
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SkipWhenPortUnavailable.PortAvailabilityCondition.class)
public @interface SkipWhenPortUnavailable {
    int value();

    public class PortAvailabilityCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            Optional<SkipWhenPortUnavailable> annotation =
                    context.getElement().flatMap(el -> AnnotationUtils.findAnnotation(el, SkipWhenPortUnavailable.class));

            return annotation.map(a -> {
                try (ServerSocket ss = new ServerSocket(a.value())) {
                    return ConditionEvaluationResult.disabled("Port " + a.value() + " is available, skipping test.");
                } catch (IOException e) {
                    // Port is in use or available depending on logic; 
                    // usually if bind fails, port is busy (available for service).
                    // Adjust logic based on whether you want to skip if port is BUSY or FREE.
                    // If checking if service is UP: try connecting, if fail -> disabled.
                    return ConditionEvaluationResult.enabled("Port " + a.value() + " check failed.");
                }
            }).orElse(ConditionEvaluationResult.enabled("No port condition found"));
        }
    }
}
