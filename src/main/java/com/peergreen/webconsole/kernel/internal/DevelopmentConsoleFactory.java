/**
 * Peergreen S.A.S. All rights reserved.
 * Proprietary and confidential.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.peergreen.webconsole.kernel.internal;

import javax.servlet.ServletException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.ConfigurationException;
import org.apache.felix.ipojo.Factory;
import org.apache.felix.ipojo.MissingHandlerException;
import org.apache.felix.ipojo.UnacceptableConfiguration;
import org.apache.felix.ipojo.annotations.Bind;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Unbind;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.ow2.util.log.Log;
import org.ow2.util.log.LogFactory;

import com.peergreen.webconsole.Constants;
import com.peergreen.webconsole.core.console.Console;
import com.peergreen.webconsole.core.osgi.VaadinOSGiServlet;
import com.peergreen.webconsole.core.vaadin7.UIProviderFactory;
import com.vaadin.server.UIProvider;

/**
 * Community Console factory
 *
 * @author Mohammed Boukada
 */
@Component
@Instantiate
public class DevelopmentConsoleFactory {

    /**
     * Logger.
     */
    private static final Log LOGGER = LogFactory.getLog(DevelopmentConsoleFactory.class);

    /**
     * Http Service
     */
    @Requires
    private HttpService httpService;

    /**
     * UI provider factory
     */
    @Requires
    private UIProviderFactory uiProviderFactory;

    @Requires(from = "com.peergreen.webconsole.core.notifier.NotifierService")
    private Factory notifierFactory;
    private Map<Console, ComponentInstance> notifierInstances = new HashMap<>();

    private List<String> aliases = new CopyOnWriteArrayList<>();

    /**
     * Bind a console
     *
     * @param console
     */
    @Bind(aggregate = true, optional = true)
    public void bindConsole(Console console, Dictionary properties) {
        if (!Constants.PRODUCTION_MODE_CONSOLE_PID.equals(properties.get("factory.name")) &&
                !Constants.DEVELOPMENT_MODE_CONSOLE_PID.equals(properties.get("factory.name"))) {
            return;
        }

        String[] domains = (String[]) properties.get(Constants.CONSOLE_DOMAINS);
        if (domains == null || domains.length == 0) {
            LOGGER.error("Please define at least one domain for ''{0}''.", properties.get(Constants.CONSOLE_NAME));
        } else {
            // Create an UI provider for the console UI
            UIProvider uiProvider = uiProviderFactory.createUIProvider(properties);
            createNotifierService(console, (String) properties.get("instance.name"));
            // Create a servlet
            VaadinOSGiServlet servlet = new VaadinOSGiServlet(uiProvider);
            try {
                // Register the servlet with the console alias
                String alias = (String) properties.get(Constants.CONSOLE_ALIAS);
                httpService.registerServlet(alias, servlet, null, null);
                aliases.add(alias);
            } catch (ServletException | NamespaceException e) {
                // ignore update
                LOGGER.warn(e);
            }
        }
    }

    private void createNotifierService(Console console, String consoleId) {
        try {
            Dictionary<String, Object> properties = new Hashtable<>();
            properties.put(Constants.CONSOLE_ID, consoleId);
            notifierInstances.put(console, notifierFactory.createComponentInstance(properties));
        } catch (UnacceptableConfiguration | MissingHandlerException | ConfigurationException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Unbind a console
     *
     * @param console
     */
    @Unbind
    public void unbindConsole(Console console, Dictionary properties) {
        // Unregister its servlet
        uiProviderFactory.stopUIProvider(properties);
        httpService.unregister((String) properties.get(Constants.CONSOLE_ALIAS));

        // Remove notifier service
        notifierInstances.get(console).stop();
        notifierInstances.get(console).dispose();
    }

    @Invalidate
    public void stop() {
        for (String alias : aliases) {
            httpService.unregister(alias);
        }
    }
}
