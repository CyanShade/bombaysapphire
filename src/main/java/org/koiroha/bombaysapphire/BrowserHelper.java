package org.koiroha.bombaysapphire;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import org.slf4j.Logger;

import java.util.function.Consumer;

public class BrowserHelper {
    public static void init(WebEngine engine, Consumer<WebEngine> succeeder){
        Logger logger = ParasitizedBrowser$.MODULE$.logger();
        engine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>(){
            @Override
            public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
                logger.debug("[" + newValue + "] " + engine.getLocation());
                if(newValue == Worker.State.SUCCEEDED){
                    succeeder.accept(engine);
                }
            }
        });
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
        engine.setOnResized((e) -> logger.trace("OnResize(" + e + ")"));
        engine.setOnStatusChanged((e) -> logger.trace("OnStatusChanged(" + e + ")"));
        engine.setOnVisibilityChanged((e) -> logger.trace("OnVisibilityChanged(" + e + ")"));
        engine.setPromptHandler((e) -> {
            logger.info("Prompt(" + e + ")");
            return "";
        });
    }
}
