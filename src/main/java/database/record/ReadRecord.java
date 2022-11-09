package database.record;

public class ReadRecord implements Record {
    private final String type;
    private final String record;

    protected ReadRecord(String type, String record) {
        this.type = type;
        this.record = record;
    }

    @Override
    public String getRecord() {
        return null;
    }

    @Override
    public String getType() {
        return null;
    }

    public static Record make(String type, String record) {
        return null;
    }
}
