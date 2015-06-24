**! ONLY A PROTOTYPE !**

# pi remote configuration
 
*currently the app use pi/pi for remote login and saves the config under /tmp on the remote host*

 * on the pi side runs a golang daemon `src/main/go/picfgd.go`
 * plug the pi per ethernet in the network with a dhcp server
 * this tool sends a broadcast, and the golang daemon reponds pi's name and dhcp address
 * when a pi is selected, the current configuration are read and displayed
 * the configuration can changed, and pushed to the remote pi.


# screenshot

### Scan Network (send's a broadcast datagram)
![Scan Network](doc/scan.png)

### Pi selected, with his current configuration
![PI Selected](doc/pi-selected.png)

### Configuration changed
![WLAN Authentication](doc/wlan-authentication-changed.png)

# current TODO's / FIXME's

    main% grep -r -E 'TODO|FIXME' src  
    src/main/scala/picfg/Scanner.scala:    //FIXME: broadcast address: 255.255.255.255 doesn't work under FreeBSD - why?
    src/main/scala/picfg/config.scala:    // FIXME: this go's under /etc/wpa_supplicant/
    src/main/scala/picfg/config.scala:    // FIXME: this go's under  /etc/network/interfaces.d/picfg-wlan0
    src/main/scala/picfg/Remote.scala:    // FIXME: add timeout -> session.connect(timeout)!
    src/main/scala/picfg/Remote.scala:      //FIXME: bad!!!
    src/main/scala/picfg/PiCfg.scala:  // FIXME: app configuration
    src/main/scala/picfg/elements.scala:    // TODO: new DefaultListCellRenderer{ ... } triggers compile-time error - why?:
    src/main/scala/picfg/elements.scala:    //FIXME: scrolling per mouse not possible!
    src/main/go/picfgd.go:			// FIXME: check msg
    src/main/go/picfgd.go:			// FIXME: use the eth0 ip - currently it use the first found ip


