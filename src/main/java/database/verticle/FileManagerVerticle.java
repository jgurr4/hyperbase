package database.verticle;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.eventbus.EventBus;
import io.vertx.rxjava3.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import static database.BusEvent.*;

public class FileManagerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileManagerVerticle.class);

    @Override
    public Completable rxStart() {
        vertx.eventBus().consumer(createRecord.name(), this::handleCreateRecord);
        vertx.eventBus().consumer(readRecord.name(), this::handleReadRecord);
        vertx.eventBus().consumer(modifyRecord.name(), this::handleModifyRecord);
        vertx.eventBus().consumer(deleteRecord.name(), this::handleDeleteRecord);
        return Completable.complete();
    }

    //NOTE: MappedByteBuffer and file mapping remain valid until the garbage is collected. sun.misc.Cleaner is probably
    // the only option available to clear memory-mapped files. see https://www.geeksforgeeks.org/what-is-memory-mapped-file-in-java/
    // This is the most complicated operation on this page.
    // This part is more complex, because the query engine will send you a string of text to match against, it will either
    // be regex or normal string of records to look for. For example, if the query was 'select * from person where person-id > 5'
    // Then the type would be person and the text to match would be 'person-id: ^[6-9][0-9]*\d$'
    // Basically that will grep the file for any person records > 5. Of course it won't parse the entire database file unless it has to.
    // That is where the more complex stuff comes in, it will check its cache to find out all the possible buckets it will need to load
    // Then it will memory map through each bucket one at a time and pull all the records from each one that match the regex condition
    private void handleReadRecord(Message message) {
        final EventBus eb = vertx.eventBus();
        LOGGER.debug("FileManagerVerticle got request to read record");
        final JsonObject messageJson = JsonObject.mapFrom(message.body());
        final JsonObject jsonReply = new JsonObject();
        final String regex = messageJson.getString("regex");
        final String type = messageJson.getString("type");
        final Single<String> indexes = eb.rxRequest(getMatchedIndexes.name(), regex).map(e -> e.body().toString());
        // Everything below is subject to change. Here we need to pull up every matched index from the main database file
        // one by one and load into memory to extract all the matched records inside them. Then we can return that
        // string of records in the eventbus reply.
        try (RandomAccessFile sc = new RandomAccessFile(type, "rw")) {
            MappedByteBuffer out = sc.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 10);
            for (int i = 0; i < text.length; i++) {
                LOGGER.debug(String.valueOf((out.put((byte) text[i]))));
            }
            LOGGER.debug("Writing to Memory is complete");
            for (int i = 0; i < text.length; i++) {
                LOGGER.debug(String.valueOf((char)out.get(i)));
            }
            LOGGER.debug("Reading from Memory is complete");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        message.reply();
    }

    // NOTE: consider using bufferedWriter instead. See https://www.digitalocean.com/community/tutorials/java-write-to-file
    private void handleCreateRecord(Message message) {
        LOGGER.debug("FileManagerVerticle got request to create record");
        final JsonObject messageJson = JsonObject.mapFrom(message.body());
        final String text = messageJson.getString("record");
        final String type = messageJson.getString("type");
        try {
            File db = new File("/etc/" + type);
            FileWriter fr = new FileWriter(db);
            if (db.createNewFile()) {
                db.setReadable(true);
                db.setWritable(true);
                System.out.println("File created: " + db.getName());
            } else {
                System.out.println("File already exists.");
                fr.append(text);
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private void handleModifyRecord(Message message) {
        LOGGER.debug("FileManagerVerticle got request to modify record");
    }

    private void handleDeleteRecord(Message message) {
        LOGGER.debug("FileManagerVerticle got request to delete record");
        final JsonObject messageJson = JsonObject.mapFrom(message.body());
        final char[] text = messageJson.getString("record").toCharArray();
        final String type = messageJson.getString("type");
        try (RandomAccessFile sc = new RandomAccessFile(type, "rw")) {
            // Mapping the file with the memory
            // Here the out is the object
            // This command will help us enable the read and
            // write functions
            MappedByteBuffer out = sc.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 10);
            // Writing into memory mapped file
            // taking it as 10 and printing it accordingly
            for (int i = 0; i < text.length; i++) {
                LOGGER.debug(String.valueOf((out.put((byte) text[i]))));
            }
            // Print the display message as soon
            // as the memory is done writing
            LOGGER.debug("Writing to Memory is complete");
            // Reading from memory mapped files
            // You can increase the size , it not be 10 , it
            // can be higher or lower Depending on the size
            // of the file
            for (int i = 0; i < text.length; i++) {
                LOGGER.debug(String.valueOf((char)out.get(i)));
            }
            // Display message on the console showcasing
            // successful execution of the program
            LOGGER.debug("Reading from Memory is complete");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
