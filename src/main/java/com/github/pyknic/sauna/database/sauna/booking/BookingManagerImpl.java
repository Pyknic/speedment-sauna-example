package com.github.pyknic.sauna.database.sauna.booking;

import com.github.pyknic.sauna.database.sauna.booking.generated.GeneratedBookingManagerImpl;
import com.speedment.Speedment;

/**
 * A manager implementation representing an entity (for example, a row) in
 * the Table sauna.db0.sauna.booking.
 * <p>
 * This file is safe to edit. It will not be overwritten by the code
 * generator.
 * 
 * @author pyknic
 */
public final class BookingManagerImpl extends GeneratedBookingManagerImpl implements BookingManager {
    
    public BookingManagerImpl(Speedment speedment) {
        super(speedment);
    }
}