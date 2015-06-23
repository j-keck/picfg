package main

import "net"
import "fmt"
import "log"
import "os"

func main() {
	hostname, ip := hostInfo()
	log.Printf("STARTUP - current hostname: '%s', ip: '%s'\n", hostname, ip)
	listen(33333)
}

func listen(port int) {
	addr, err := net.ResolveUDPAddr("udp", fmt.Sprintf(":%d", port))
	panicOnErr(err, "ResolveUDPAddr")

	log.Printf("listen on port: %d\n", port)
	socket, err := net.ListenUDP("udp", addr)
	panicOnErr(err, "ListenUDP")
	defer socket.Close()

	for {
		buf := make([]byte, 256)
		len, remote, err := socket.ReadFromUDP(buf)
		if err != nil {
			log.Printf("exception when reading from network: %s\n", err.Error())
		} else {
			msg := string(buf[:len])
			log.Printf("received: '%s', from: %s\n", msg, remote.String())

			// FIXME: check msg
			go respond(remote)
		}
	}
}

func respond(remote *net.UDPAddr) {
	hostname, ip := hostInfo()
	msg := fmt.Sprintf("%s:%s", hostname, ip)

	log.Printf("respond: '%s', to: %s\n", msg, remote.String())
	con, _ := net.Dial("udp", remote.String())
	con.Write([]byte(msg))
}

func hostInfo() (string, string) {
	addrs, err := net.InterfaceAddrs()
	panicOnErr(err, "InterfaceAddrs")

	var ip string
	for _, addr := range addrs {
		if ipNet, ok := addr.(*net.IPNet); ok {
			if ipNet.IP.IsLoopback() {
				// ignore loopback interface
				continue
			}

			// FIXME: use the eth0 ip - currently it use the first found ip
			ip = ipNet.IP.To4().String()
			break
		}
	}

	hostname, _ := os.Hostname()

	return hostname, ip
}

func panicOnErr(err error, msg ...string) {
	if err != nil {
		panic(fmt.Sprintf("%s: %s", msg, err))
	}
}
