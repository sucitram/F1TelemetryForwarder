package com.github.sucitram.tools

import groovy.cli.picocli.CliBuilder
import groovy.cli.picocli.OptionAccessor

/**
 * Read UDP datagrams from the local game socket (can be from the game of from another tool which processes the data)
 * Read the packet type from the header and forward all non-configurable packets to the destination.
 * Filter known packet types with configurable frequency to ensure we don't exceed the required threshold.
 *
 * This was written to allow simvibe to operate at 60hz and simdashboard to operate at 20hz
 *
 * Specification for packets defined on codemasters forums
 * https://forums.codemasters.com/topic/44592-f1-2019-udp-specification/
 */

class F1TelemetryFilter extends Thread {
    DatagramSocket gameSocket
    byte[] buf = new byte[1347] // max size of a f1 2019 datagram

    Set<TelemetryDestination> applications = new HashSet<TelemetryDestination>()


    F1TelemetryFilter(int portFrom) {
        gameSocket = new DatagramSocket(portFrom)
    }

    /**
     * Add destination application to forward packets to
     * @param destination application
     * @return this - to support chaining
     */
    F1TelemetryFilter addPortForward(TelemetryDestination destination) {
        applications.add(destination)
        return this
    }

    /**
     * Run the utility.
     */
    void run() {
        println "Read game:${gameSocket.getLocalPort()} "
        applications.each {
            println "     destination: $it"
        }

        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length)
            gameSocket.receive(packet)

            applications.each{it.forward(packet)}
        }
    }

    public static void main(args) {

        CliBuilder cli = new CliBuilder(usage:"F1TelemetryForwarder")
        cli.h(argName:'help', type:boolean, "Display this usage information")
        cli.p(argName:'port', type:int, "Game UDP output port")
        cli.d(argName:'destination', type: List, auxiliaryTypes: String, "Destination in form address:port:frequency (frequency optional)")

        OptionAccessor params = cli.parse(args)
        if (params.h || !(params.d && params.p)) {
            cli.usage()
            return
        }

        params.ds.inject(new F1TelemetryFilter(params.p)) {filter, destination ->
            return filter.addPortForward(new TelemetryDestination(destination.split(":")))
        }.run()
    }
}
