package database.verticle;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static database.BusEvent.*;

public class CacherVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileManagerVerticle.class);

    @Override
    public Completable rxStart() {
        vertx.eventBus().consumer(getCachedRecords.name(), this::handleGetCachedRecords);
        vertx.eventBus().consumer(getMatchedIndexes.name(), this::handleGetMatchedIndexes);
        return Completable.complete();
    }

    private void handleGetMatchedIndexes(Message message) {
        // This method is used to get all the cached indexes/buckets from memory for a particular database
        // file based on the type, and only the ones that are matching the regex.
    }

    private void handleGetCachedRecords(Message message) {
        // This method is used to retrieve all the cached records from memory for a particular
        // database file based on the type.
    }
}
