package com.github.pyknic.sauna;

import com.github.pyknic.sauna.database.sauna.booking.Booking;
import com.github.pyknic.sauna.generated.GeneratedSaunaApplication;
import com.github.pyknic.sauna.view.BookingView;
import com.speedment.Speedment;
import com.speedment.internal.logging.Level;
import com.speedment.internal.logging.LoggerManager;
import com.speedment.manager.Manager;
import java.security.SecureRandom;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import static java.time.temporal.ChronoUnit.DAYS;

/**
 * A {@link
 * com.speedment.internal.core.runtime.SpeedmentApplicationLifecycle} class
 * for the {@link com.speedment.config.db.Project} named sauna. representing
 * an entity (for example, a row) in the Project sauna.
 * <p>
 * This file is safe to edit. It will not be overwritten by the code
 * generator.
 * 
 * @author pyknic
 */
public final class SaunaApplication extends GeneratedSaunaApplication {
    
    public static void main(String... params) {
        final Speedment speedment = new SaunaApplication()
            .withPassword("password")
            .build();
        
        final Manager<Booking> bookings = speedment.managerOf(Booking.class);
        
        final SecureRandom rand = new SecureRandom();
        rand.setSeed(System.currentTimeMillis());
        
        // Insert three new bookings into the system.
        bookings.newEmptyEntity()
            .setBookingId(rand.nextLong())
            .setEventType("CREATE")
            .setSauna(1)
            .setTenant(1)
            .setBookedFrom(Date.valueOf(LocalDate.now().plus(3, DAYS)))
            .setBookedTo(Date.valueOf(LocalDate.now().plus(5, DAYS)))
            .persist();
        
        bookings.newEmptyEntity()
            .setBookingId(rand.nextLong())
            .setEventType("CREATE")
            .setSauna(1)
            .setTenant(2)
            .setBookedFrom(Date.valueOf(LocalDate.now().plus(1, DAYS)))
            .setBookedTo(Date.valueOf(LocalDate.now().plus(2, DAYS)))
            .persist();
        
        bookings.newEmptyEntity()
            .setBookingId(rand.nextLong())
            .setEventType("CREATE")
            .setSauna(1)
            .setTenant(3)
            .setBookedFrom(Date.valueOf(LocalDate.now().plus(2, DAYS)))
            .setBookedTo(Date.valueOf(LocalDate.now().plus(7, DAYS)))
            .persist();
        
        LoggerManager.getLogger(BookingView.class).setLevel(Level.DEBUG);
        final BookingView view = BookingView.create(bookings);
        
        // Wait until the view is up-to-date.
        try { Thread.sleep(5_000); }
        catch (final InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        
        System.out.println("Current Bookings for Sauna 1:");
        final SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd");
        view.stream()
            .filter(Booking.SAUNA.equal(1))
            .filter(Booking.BOOKED_TO.greaterOrEqual(Date.valueOf(LocalDate.now())))
            .sorted(Booking.BOOKED_FROM.comparator())
            .map(b -> String.format(
                "Booked from %s to %s by Tenant %d.", 
                dt.format(b.getBookedFrom().get()),
                dt.format(b.getBookedTo().get()),
                b.getTenant().get()
            ))
            .forEachOrdered(System.out::println);
        
        System.out.println("No more bookings!");
        view.stop();
    }
}