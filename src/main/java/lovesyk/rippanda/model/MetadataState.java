package lovesyk.rippanda.model;

public enum MetadataState {
    DISK(0), DISK_UP_TO_DATE(1), ONLINE(2);

    private final int value;

    MetadataState(final int newValue) {
        value = newValue;
    }

    public int getValue() {
        return value;
    }
}
