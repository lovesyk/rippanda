package lovesyk.rippanda;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.service.archival.api.IArchivalService;
import lovesyk.rippanda.settings.Settings;

/**
 * The main application class.
 */
public class Application {
    private static final Logger LOGGER = LogManager.getLogger(Application.class);

    /**
     * Launches the application.
     * 
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        LOGGER.info("Starting application...");
        int status = 1;

        SeContainerInitializer initializer = SeContainerInitializer.newInstance().disableDiscovery().addPackages(true,
                Application.class);
        try (SeContainer container = initializer.initialize()) {
            Settings settings = container.select(Settings.class).get();
            settings.init(args);

            Instance<IArchivalService> archivalServices = container.select(IArchivalService.class);
            for (IArchivalService archivalService : archivalServices) {
                archivalService.process();
            }

            LOGGER.info("Processing finished successfully.");
            status = 0;
        } catch (RipPandaException e) {
            LOGGER.error("An error occurred.", e);
        } catch (InterruptedException e) {
            LOGGER.warn("Processing was interrupted.", e);
            status = 130;
        } catch (Throwable e) {
            LOGGER.error("An unexpected error occurred.", e);
        }

        System.exit(status);
    }
}
