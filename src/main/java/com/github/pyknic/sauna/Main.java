package com.github.pyknic.sauna;

import com.github.pyknic.sauna.booking.Booking;
import com.github.pyknic.sauna.booking.BookingImpl;
import com.github.pyknic.sauna.booking.BookingManager;
import com.github.pyknic.sauna.view.BookingView;
import java.security.SecureRandom;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import static java.time.temporal.ChronoUnit.DAYS;

/**
 *
 * @author Emil Forslund
 * @since  1.0.0
 */
public final class Main {

    public static void main(String... params) {
        final SaunaApplication app = new SaunaApplicationBuilder()
            .withPassword("password")
            .build();
        
        final BookingManager bookings = app.getOrThrow(BookingManager.class);
        
        final SecureRandom rand = new SecureRandom();
        rand.setSeed(System.currentTimeMillis());
        
        // Insert three new bookings into the system.
        bookings.persist(
            new BookingImpl()
                .setBookingId(rand.nextLong())
                .setEventType("CREATE")
                .setSauna(1)
                .setTenant(1)
                .setBookedFrom(Date.valueOf(LocalDate.now().plus(3, DAYS)))
                .setBookedTo(Date.valueOf(LocalDate.now().plus(5, DAYS)))
        );

        bookings.persist(
            new BookingImpl()
                .setBookingId(rand.nextLong())
                .setEventType("CREATE")
                .setSauna(1)
                .setTenant(2)
                .setBookedFrom(Date.valueOf(LocalDate.now().plus(1, DAYS)))
                .setBookedTo(Date.valueOf(LocalDate.now().plus(2, DAYS)))
        );

        bookings.persist(
            new BookingImpl()
                .setBookingId(rand.nextLong())
                .setEventType("CREATE")
                .setSauna(1)
                .setTenant(3)
                .setBookedFrom(Date.valueOf(LocalDate.now().plus(2, DAYS)))
                .setBookedTo(Date.valueOf(LocalDate.now().plus(7, DAYS)))
        );

        final BookingView view = BookingView.create(bookings);

        // Wait until the view is up-to-date.
        try { Thread.sleep(5_000); }
        catch (final InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        System.out.println("Current Bookings for Sauna 1:");
        final SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd");
        final Date now = Date.valueOf(LocalDate.now());
        view.stream()
            .filter(Booking.SAUNA.equal(1))
            .filter(Booking.BOOKED_TO.greaterOrEqual(now))
            .sorted(Booking.BOOKED_FROM.comparator())
            .map(b -> String.format(
                "Booked from %s to %s by Tenant %d.", 
                dt.format(b.getBookedFrom().get()),
                dt.format(b.getBookedTo().get()),
                b.getTenant().getAsInt()
            ))
            .forEachOrdered(System.out::println);

        System.out.println("No more bookings!");
        view.stop();
    }
    
}