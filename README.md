# Speedment Sauna Example
A booking application for a communal Sauna that is used to showcase how to use Speedment, Event Sourcing and CQRS.

## Usage
Here is an example of how a materialized view can be created and queried to show the current bookings.
```java
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
```
