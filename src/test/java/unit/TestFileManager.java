package unit;

import database.record.*;
import database.record.Record;
import database.verticle.FileManagerVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.eventbus.EventBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static database.BusEvent.*;

@ExtendWith(VertxExtension.class)
public class TestFileManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileManagerVerticle.class);

    @Test
    public void testRecordExists(Vertx vertx, VertxTestContext context) {
        final Record createRecord = CreateRecord.make("person", "john");
        vertx.eventBus().rxRequest(recordExists.name(), createRecord)
            .cast(Boolean.TYPE)
            .subscribe(result -> {
                if (result) {
                    context.completeNow();
                }
                context.failNow("Record seems to exist");
            });
    }

    @Test
    public void testCreateRecord(Vertx vertx, VertxTestContext context) {
        final EventBus eb = vertx.eventBus();
        final Record createR = CreateRecord.make("file", "fileA");
        JsonObject jsonMsg = JsonObject.of("record", createR, "type", createR.getType());
        FileManagerVerticle fileManagerVerticle = new FileManagerVerticle();
        vertx.rxDeployVerticle(fileManagerVerticle)
//                .flatMap(e -> {
//                    return vertx.rxDeployVerticle(new UserVerticle(rs));   //This is how you deploy more than one verticle. Just use more .flatmaps().
//                })
            .subscribe(e -> {
                eb.rxRequest(createRecord.name(), jsonMsg)
                    .subscribe(ar -> {
//                        JsonObject json = new JsonObject(ar.body().toString());
//                        if (!json.getString("error").equals("")) {
//                            context.failNow(new Exception("createRecord function failed to create new record."));
//                        } else {
//                            context.completeNow();
//                        }
                    },
                    err -> {
                        LOGGER.debug("createRecord replied with: " + err);
                    });
                },
                err -> {
                    context.failNow(err);
                });
    }
}
