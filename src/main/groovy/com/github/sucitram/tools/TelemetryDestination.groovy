package com.github.sucitram.tools

/**
 * Destination application configuration for telemetry data.
 */
class TelemetryDestination {
    DatagramSocket socket
    InetAddress host
    int port
    int rateHz
    int throttleWindow

    // capture time last packet of each type was transmitted to support simple throttling
    long[] packetMillis = new long[10]
    static int game, major, minor, printPacketTypes = -1

    /**
     * Destination application
     *
     * @param host server hosting the application
     * @param port application listens on
     * @param rateHz maximum rate of configurable packets to be transmitted
     */
    TelemetryDestination(String host, int port, int rateHz) {
        this.host = InetAddress.getByName(host)
        this.port = port
        this.rateHz = rateHz
        this.throttleWindow = Math.ceil(1000/rateHz).toInteger()
        8.times { packetMillis[it] = 0 }

        socket = new DatagramSocket()
    }

    /**
     * Build a telemetry destination from 2-3 destination parts
     *   - address
     *   - port
     *   - frequency (default to 1000 - max game transmission rate is only 60)
     *
     * @param destinationParts as split from address:port:rate
     */
    TelemetryDestination(String... destinationParts) {
        this(destinationParts[0], destinationParts[1] as int, destinationParts.length>2?destinationParts[2] as int:1000)
    }

    void forward(DatagramPacket packet) {
        // Relatively easy to read the year from the packet if we wanted to support other games
        // https://forums.codemasters.com/topic/44592-f1-2019-udp-specification/
        int packetType = uint8(packet, 5)
        boolean shouldFilter = [0,2,6,7].contains(packetType) // configurable frequency packets

        long now = System.currentTimeMillis()
        if (shouldFilter && packetMillis[packetType] + throttleWindow > now) {
            return // should not forward - within throttle window
        }

        // detect game version
        int year = uint16(packet, 0)
        int verMajor = uint8(packet, 2)
        int verMinor = uint8(packet, 3)

        if (year != game || verMajor != major || verMinor != minor) {
            game = year
            major = verMajor
            minor = verMinor

            println "Detected F1 $game v$major.$minor"
            printPacketTypes = 1000
        }

        if (printPacketTypes >= 0) {
            printPacketTypes--
            if (printPacketTypes == 0) {
                println "     Received Packet Types: ${Arrays.asList(packetMillis).withIndex().collect { long entry, int i -> entry != 0 ? i : null } - null}"
            }
        }
        packetMillis[packetType] = now
        DatagramPacket forward = new DatagramPacket(packet.getData(), packet.getLength(), host, port)
        socket.send(forward)
    }

    String toString() {
        return "host:${host.canonicalHostName} port:$port frequency:${rateHz}hz period:${throttleWindow}ms"
    }

    static int uint16(DatagramPacket packet, offset) {
        return ((packet.getData()[offset+1] & 0xFF) << 8) | (packet.getData()[offset+0] & 0xFF)
    }

    static int uint8(DatagramPacket packet, offset) {
        return (packet.getData()[offset] & 0xFF)
    }
}
