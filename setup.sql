CREATE DATABASE sauna;

CREATE TABLE `sauna`.`booking` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `booking_id` BIGINT NOT NULL,
  `event_type` ENUM('CREATE', 'UPDATE', 'DELETE') NOT NULL,
  `tenant` INT NULL,
  `sauna` INT NULL,
  `booked_from` DATE NULL,
  `booked_to` DATE NULL,
  PRIMARY KEY (`id`)
);
