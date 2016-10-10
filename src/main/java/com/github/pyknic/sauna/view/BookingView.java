package com.github.pyknic.sauna.view;

import com.github.pyknic.sauna.booking.Booking;
import com.github.pyknic.sauna.booking.BookingImpl;
import com.github.pyknic.sauna.booking.BookingManager;
import static com.speedment.runtime.core.util.OptionalUtil.unwrap;
import static java.util.Collections.unmodifiableList;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;

/**
 *
 * @author Emil Forslund
 */
public final class BookingView {
    
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
    
    private boolean accept(Booking ev) {
        final String type = ev.getEventType();

        // If this was a creation event
        switch (type) {
            case "CREATE" :
                // Creation events must contain all information.
                if (!ev.getSauna().isPresent()
                ||  !ev.getTenant().isPresent()
                ||  !ev.getBookedFrom().isPresent()
                ||  !ev.getBookedTo().isPresent()
                ||  !checkIfAllowed(ev)) {
                    return false;
                }

                // If something is already mapped to that key, refuse the event.
                return bookings.putIfAbsent(ev.getBookingId(), ev) == null;

            case "UPDATE" :
                // Create a copy of the current state
                final Booking existing = bookings.get(ev.getBookingId());

                // If the specified key did not exist, refuse the event.
                if (existing != null) {
                    final Booking proposed = new BookingImpl();
                    proposed.setId(existing.getId());

                    // Update non-null values
                    proposed.setSauna(ev.getSauna().orElse(
                        unwrap(existing.getSauna())
                    ));
                    proposed.setTenant(ev.getTenant().orElse(
                        unwrap(existing.getTenant())
                    ));
                    proposed.setBookedFrom(ev.getBookedFrom().orElse(
                        unwrap(existing.getBookedFrom())
                    ));
                    proposed.setBookedTo(ev.getBookedTo().orElse(
                        unwrap(existing.getBookedTo())
                    ));

                    // Make sure these changes are allowed.
                    if (checkIfAllowed(proposed)) {
                        bookings.put(ev.getBookingId(), proposed);
                        return true;
                    }
                }

                return false;

            case "DELETE" :
                // Remove the event if it exists, else refuse the event.
                return bookings.remove(ev.getBookingId()) != null;

            default :
                System.out.format("Unexpected type '%s' was refused.%n", type);
                return false;
        }
    }
    
    private boolean checkIfAllowed(Booking booking) {
        // Bookings where the end date is after the start are always invalid.
        // (Same start and end date is okey).
        if (booking.getBookedFrom().get().after(booking.getBookedTo().get())) {
            return false;
        }
        
        // Make sure there is no other booking made for the same sauna or by the 
        // same tenant during this time.
        return (bookings.entrySet().stream()
            
            // Exclude this booking from the search.
            .filter(e -> !e.getKey().equals(booking.getBookingId()))
            .map(Map.Entry::getValue)
            
            // Only consider bookings about the same sauna or the same tenant
            .filter(b -> 
                   b.getSauna().getAsInt()  == booking.getSauna().getAsInt()
                || b.getTenant().getAsInt() == booking.getTenant().getAsInt()
            )
            
            // If any booking collides time-wise, refuse this event.
            .noneMatch(b -> 
                !( b.getBookedTo().get().before(booking.getBookedFrom().get())
                || b.getBookedFrom().get().after(booking.getBookedTo().get()))
            )
        );
    }
    
    public static BookingView create(BookingManager manager) {
        final AtomicBoolean working = new AtomicBoolean(false);
        final AtomicLong last = new AtomicLong();
        final AtomicLong total = new AtomicLong();
        
        final String tableName = manager.getTableIdentifier().getTableName();
        final String fieldName = Booking.ID.identifier().getColumnName();

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

                        // Loop until no events was merged 
                        // (the database is up to date).
                        while (true) {

                            // Get a list of up to 25 events that has not yet 
                            // been merged into the materialized object view.
                            final List<Booking> added = unmodifiableList(
                                manager.stream()
                                    .filter(Booking.ID.greaterThan(last.get()))
                                    .limit(MAX_BATCH_SIZE)
                                    .sorted(Booking.ID.comparator())
                                    .collect(toList())
                            );

                            if (added.isEmpty()) {
                                if (!first) {
                                    System.out.format(
                                        "%s: View is up to date. A total of " + 
                                        "%d rows have been loaded.%n",
                                        System.identityHashCode(last),
                                        total.get()
                                    );
                                }

                                break;
                            } else {
                                final Booking lastEntity = added.get(
                                    added.size() - 1
                                );
                                
                                last.set(lastEntity.getId());
                                added.forEach(view::accept);
                                total.addAndGet(added.size());

                                System.out.format(
                                    "%s: Downloaded %d row(s) from %s. " + 
                                    "Latest %s: %d.%n", 
                                    System.identityHashCode(last),
                                    added.size(),
                                    tableName,
                                    fieldName,
                                    Long.parseLong("" + last.get())
                                );
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