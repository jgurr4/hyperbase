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
    // because the Record Maker will send you a string of text to match against, it will either
    // be regex or normal string of record values to look for. For example, if the query was 'select * from person where id > 5'
    // Then the type would be person and the text to match would be 'person-id: ^[6-9][0-9]*\d$'
    // Basically that will grep the file for any person records > 5. Of course it won't parse the entire database file unless it has to.
    // Another example: select * from person where name = 'john', age = 5; would search for all records with "name: john" and "age: 5"
    // That is where the more complex stuff comes in, it will check its cache to find out all the possible buckets it will need to load
    // Then it will memory map through each bucket one at a time and pull all the records from each one that match the regex condition
    // It will return a list of ReadRecords in the reply.
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

    // See https://www.digitalocean.com/community/tutorials/java-write-to-file
    // This method works like this:
    // First, the Record factory converts a query into a list of records that are formatted correctly beforehand.
    // Then this method must take that pre-formatted list of records and validate that none of them already exist.
    // any that do exist are removed from the list, the rest are appended to the end of the
    // correct database file type
    private void handleCreateRecord(Message message) {
        LOGGER.debug("FileManagerVerticle got request to create record");
        final JsonObject jsonMsg = JsonObject.mapFrom(message.body());
        final String[] records = jsonMsg.getString("records").split("\n");
        final String type = jsonMsg.getString("type");
        try {
            File db = new File("/etc/" + type);
            FileWriter fw = new FileWriter(db);
            BufferedWriter bw = new BufferedWriter(fw);
            if (db.createNewFile()) {
                db.setReadable(true);
                db.setWritable(true);
                LOGGER.debug("File created: " + db.getName());
            } else {
                LOGGER.debug("File already exists.");
            }
            for (int i = 0; i < records.length; i++) {
                if (!recordExists(records[i], type)) {
                    bw.append(records[i]);
                } else {
                    LOGGER.debug("record: `" + records[i] + "` already exists in " + type + " database file");
                }
            }
            bw.close();
            fw.close();
        } catch (IOException e) {
            LOGGER.debug("An error occurred.");
            e.printStackTrace();
        }
    }

    // This method works like this:
    // First the Record Maker converts a query into a formatted list of modify records and sends it here.
    // Then this method checks to make sure the records all exist, any that don't exist are taken off the list.
    // Then the remaining list is appended into the type file, and the reply message will contain the result and
    // include any warnings if debug mode is on.
    private void handleModifyRecord(Message message) {
        LOGGER.debug("FileManagerVerticle got request to modify record");
        final JsonObject jsonMsg = JsonObject.mapFrom(message.body());
        final String[] records = jsonMsg.getString("records").split("\n");
        final String type = jsonMsg.getString("type");
        try {
            File db = new File("/etc/" + type);
            FileWriter fw = new FileWriter(db);
            BufferedWriter bw = new BufferedWriter(fw);
            if (db.createNewFile()) {
                db.setReadable(true);
                db.setWritable(true);
                LOGGER.debug("File created: " + db.getName());
            } else {
                LOGGER.debug("File already exists.");
            }
            for (int i = 0; i < records.length; i++) {
                if (recordExists(records[i], type)) {
                    bw.append(records[i]);
                } else {
                    LOGGER.debug("record: `" + records[i] + "` already exists in " + type + " database file");
                }
            }
            bw.close();
            fw.close();
        } catch (IOException e) {
            LOGGER.debug("An error occurred.");
            e.printStackTrace();
        }
    }

    // This method works like this:
    // First the Query engine converts a query into a formatted list of delete records and sends them here.
    // Then this method checks to make sure the records all exist, any that don't exist are removed from the
    // list, then the reply message will notify of all records that didn't exist if debug mode is on.
    private void handleDeleteRecord(Message message) {
        LOGGER.debug("FileManagerVerticle got request to delete record");
        final JsonObject jsonMsg = JsonObject.mapFrom(message.body());
        final String[] records = jsonMsg.getString("records").split("\n");
        final String type = jsonMsg.getString("type");
        try {
            File db = new File("/etc/" + type);
            FileWriter fw = new FileWriter(db);
            BufferedWriter bw = new BufferedWriter(fw);
            if (db.createNewFile()) {
                db.setReadable(true);
                db.setWritable(true);
                LOGGER.debug("File created: " + db.getName());
            } else {
                LOGGER.debug("File already exists.");
            }
            for (int i = 0; i < records.length; i++) {
                if (recordExists(records[i], type)) {
                    bw.append(records[i]);
                } else {
                    LOGGER.debug("record: `" + records[i] + "` already exists in " + type + " database file");
                }
            }
            bw.close();
            fw.close();
        } catch (IOException e) {
            LOGGER.debug("An error occurred.");
            e.printStackTrace();
        }
    }

    // This method communicates with cacherVerticle to check the indexes of the database file for the record to
    // validate if it exists. If it doesn't exist, then this returns false.
    private boolean recordExists(String record, String type) {
        char[] text = record.toCharArray();
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
        return false;
    }

}
