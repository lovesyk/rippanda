package lovesyk.rippanda.settings;

import java.time.Duration;

import lovesyk.rippanda.exception.RipPandaException;

/**
 * Denotes an update interval with min / max values.
 */
public class UpdateInterval {
    private final Duration minThreshold;
    private final Duration minDuration;
    private final Duration maxThreshold;
    private final Duration maxDuration;

    /**
     * Instantiates a new update interval.
     * 
     * @param minThreshold the threshold of the minimum permitted duration
     * @param minDuration  the minimum permitted duration
     * @param maxThreshold the threshold of the maximum permitted duration
     * @param maxDuration  the maximum permitted duration
     * @throws RipPandaException if the state is invalid
     */
    public UpdateInterval(Duration minThreshold, Duration minDuration, Duration maxThreshold, Duration maxDuration) throws RipPandaException {
        if (minThreshold.compareTo(maxThreshold) > 0 || minDuration.compareTo(maxDuration) > 0) {
            throw new RipPandaException("Minimum update threshold or duration must not be smaller than the maximum one.");
        }
        this.minThreshold = minThreshold;
        this.minDuration = minDuration;
        this.maxThreshold = maxThreshold;
        this.maxDuration = maxDuration;
    }

    /**
     * Gets the threshold to apply the minimum duration to.
     * 
     * @return the minimum threshold
     */
    public Duration getMinThreshold() {
        return minThreshold;
    }

    /**
     * Gets the minimum permitted duration.
     * 
     * @return the minimum duration
     */
    public Duration getMinDuration() {
        return minDuration;
    }

    /**
     * Gets the threshold to apply the maximum duration to.
     * 
     * @return the maximum threshold
     */
    public Duration getMaxThreshold() {
        return maxThreshold;
    }

    /**
     * Gets the maximum permitted duration.
     * 
     * @return the maximum duration
     */
    public Duration getMaxDuration() {
        return maxDuration;
    }
}
