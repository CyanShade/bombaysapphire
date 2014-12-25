package org.koiroha.bombaysapphire;

import javafx.scene.web.WebEngine;
import org.slf4j.Logger;

public class BrowserHelper {
    public static void init(WebEngine engine){
        Logger logger = Browser$.MODULE$.logger();
        engine.setConfirmHandler((param) -> {
            logger.warn("ContentHandler(" + param + ")");
            return true;
        });
        engine.setOnError((e) -> logger.error("OnError(" + e.getMessage() + ")", e.getException()));
        engine.setCreatePopupHandler((param) -> {
            logger.warn("CreatePopup(" + param + ")");
            return null;
        });
        engine.setOnAlert((e) -> logger.warn("OnAlert(" + e + ")"));
        engine.setOnResized((e) -> logger.info("OnResize(" + e + ")"));
        engine.setOnStatusChanged((e) -> logger.info("OnStatusChanged(" + e + ")"));
        engine.setOnVisibilityChanged((e) -> logger.info("OnVisibilityChanged(" + e + ")"));
        engine.setPromptHandler((e) -> {
            logger.info("Prompt(" + e + ")");
            return "";
        });
    }
}
