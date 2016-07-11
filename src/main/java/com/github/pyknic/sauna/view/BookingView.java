package com.github.pyknic.sauna.view;

import com.github.pyknic.sauna.database.sauna.booking.Booking;
import com.speedment.internal.logging.Logger;
import com.speedment.internal.logging.LoggerManager;
import com.speedment.manager.Manager;
import com.speedment.stream.MapStream;
import static java.util.Collections.unmodifiableList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import static java.util.stream.Collectors.toList;
import java.util.concurrent.ConcurrentHashMap;
import static java.util.Objects.requireNonNull;
import java.util.stream.Stream;

/**
 *
 * @author Emil Forslund
 */
public final class BookingView {
    
    private final static Logger LOGGER = LoggerManager.getLogger(BookingView.class);
    private final static int MAX_BATCH_SIZE = 25;
    private final static int UPDATE_EVERY = 1_000; // Milliseconds

    private final Timer timer;
    private final Map<Long, Booking> bookings;

    private BookingView(Timer timer) {
        this.timer = requireNonNull(timer);
        this.bookings = new ConcurrentHashMap<>();
    }

    public Stream<Booking> stream() {
        return bookings.values().stream();
    }

    public void stop() {
        timer.cancel();
    }
    
private boolean accept(Booking event) {
    final String eventType = event.getEventType();

    // If this was a creation event
    switch (eventType) {
        case "CREATE" :
            // Creation events must contain all information.
            if (!event.getSauna().isPresent()
            ||  !event.getTenant().isPresent()
            ||  !event.getBookedFrom().isPresent()
            ||  !event.getBookedTo().isPresent()
            ||  !checkIfAllowed(event)) {
                return false;
            }

            // If there was already something mapped to that key, refuse the event.
            return bookings.putIfAbsent(event.getBookingId(), event) == null;

        case "UPDATE" :
            // Create a copy of the current state
            final Booking proposed = bookings.get(event.getBookingId()).copy();

            // If the specified key did not exist, refuse the event.
            if (proposed != null) {

                // Update non-null values
                event.getSauna().ifPresent(proposed::setSauna);
                event.getTenant().ifPresent(proposed::setTenant);
                event.getBookedFrom().ifPresent(proposed::setBookedFrom);
                event.getBookedTo().ifPresent(proposed::setBookedTo);

                // Make sure these changes are allowed.
                if (checkIfAllowed(proposed)) {
                    bookings.put(event.getBookingId(), proposed);
                    return true;
                }
            }

            return false;

        case "DELETE" :
            // Remove the event if it exists, else refuse the event.
            return bookings.remove(event.getBookingId()) != null;

        default :
            LOGGER.debug("Unexpected event type '" + eventType + "' was refused.");
            return false;
    }
}
    
    private boolean checkIfAllowed(Booking booking) {
        // Bookings where the end date is after the start date are always invalid.
        // (Same start and end date is okey).
        if (booking.getBookedFrom().get().after(booking.getBookedTo().get())) {
            return false;
        }
        
        // Make sure there is no other booking made for the same sauna or by the 
        // same tenant during this time.
        return (MapStream.of(bookings)
            
            // Exclude this booking from the search.
            .filterKey(id -> !id.equals(booking.getBookingId()))
            .values()
            
            // Only consider bookings about the same sauna or the same tenant
            .filter(b -> 
                   b.getSauna().get().equals(booking.getSauna().get())
                || b.getTenant().get().equals(booking.getTenant().get())
            )
            
            // If any booking collides time-wise, refuse this event.
            .noneMatch(b -> 
                !( b.getBookedTo().get().before(booking.getBookedFrom().get())
                || b.getBookedFrom().get().after(booking.getBookedTo().get()))
            )
        );
    }
    
    public static BookingView create(Manager<Booking> manager) {
        final AtomicBoolean working = new AtomicBoolean(false);
        final AtomicLong last = new AtomicLong();
        final AtomicLong total = new AtomicLong();
        
        final String managerName = manager.getTable().getName();
        final String fieldName = Booking.ID.getIdentifier().columnName();

        final Timer timer = new Timer();
        final BookingView view = new BookingView(timer);
        
        // Create a timed task that will execute every second.
        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                boolean first = true;

                // Make sure no previous task is already inside this block.
                if (working.compareAndSet(false, true)) {
                    try {

                        // Loop until no events was merged (the database is up to date).
                        while (true) {

                            // Get a list of up to 25 events that has not yet been merged
                            // into the materialized object view.
                            final List<Booking> added = unmodifiableList(
                                manager.stream()
                                    .filter(Booking.ID.greaterThan(last.get()))
                                    .limit(MAX_BATCH_SIZE)
                                    .sorted(Booking.ID.comparator())
                                    .collect(toList())
                            );

                            if (added.isEmpty()) {
                                if (!first) {
                                    LOGGER.debug(String.format(
                                        "%s: View is up to date. A total of %d rows have been loaded.",
                                        System.identityHashCode(last),
                                        total.get()
                                    ));
                                }

                                break;
                            } else {
                                final Booking lastEntity = added.get(added.size() - 1);
                                last.set(lastEntity.getId());

                                added.forEach(view::accept);
                                total.addAndGet(added.size());

                                LOGGER.debug(String.format(
                                    "%s: Downloaded %d row(s) from %s. Latest %s: %d.", 
                                    System.identityHashCode(last),
                                    added.size(),
                                    managerName,
                                    fieldName,
                                    Long.parseLong("" + last.get())
                                ));
                            }

                            first = false;
                        }

                    // Release this resource once we exit this block.
                    } finally {
                        working.set(false);
                    }
                }
            }
        };

        timer.scheduleAtFixedRate(task, 0, UPDATE_EVERY);
        return view;
    }
}