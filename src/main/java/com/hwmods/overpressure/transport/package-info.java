/**
 * Central pneumatic transport architecture.
 *
 * <p>Ownership rules:</p>
 * <ul>
 *   <li>{@link com.hwmods.overpressure.transport.TransportCargo} owns only the item payload.</li>
 *   <li>{@link com.hwmods.overpressure.transport.TransportRoute} owns the selected graph route.</li>
 *   <li>{@link com.hwmods.overpressure.transport.TransitEntry} owns mutable server movement state.</li>
 *   <li>{@link com.hwmods.overpressure.transport.TubeTransportManager} owns entries and occupancy.</li>
 *   <li>Block entities expose node roles and retain only configuration or render/persistence adapters.</li>
 * </ul>
 *
 * <p>New functional blocks should implement the smallest applicable role interface rather than
 * adding concrete-type checks to the route planner or transport manager.</p>
 */
package com.hwmods.overpressure.transport;
