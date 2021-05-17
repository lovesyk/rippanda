package lovesyk.rippanda.settings;

import java.time.Duration;

import lovesyk.rippanda.exception.RipPandaException;

/**
 * Denotes an update interval with multiple durations.
 */
public class UpdateInterval {
    private final Duration minDuration;
    private final Duration maxDuration;

    /**
     * Instantiates a new update interval.
     * 
     * @param minDuration the minimum permitted duration
     * @param maxDuration the maximum permitted duration
     * @throws RipPandaException if minimum duration exceeds maximum one
     */
    public UpdateInterval(Duration minDuration, Duration maxDuration) throws RipPandaException {
        if (minDuration.compareTo(maxDuration) > 0) {
            throw new RipPandaException("Minimum update duration must not be smaller than the maximum one.");
        }
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
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
     * Gets the maximum permitted duration.
     * 
     * @return the maximum duration
     */
    public Duration getMaxDuration() {
        return maxDuration;
    }
}
