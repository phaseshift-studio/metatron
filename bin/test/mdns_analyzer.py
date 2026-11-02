#!/usr/bin/env python3
"""
mdns_analyzer -- a fake metatron peer, and a reader of real ones.

It does two things at once by default: ANNOUNCES a DNS-SD service that
mdnsDiscovery will admit into /network/roster/machine, and BROWSES for the same
service type, printing for every responder it hears the roster entry metatron
would compute from it.

That second part is the point of the name. The functions below -- scheme(),
route(), segment(), machine_key(), dialable() -- are line-for-line ports of
mdnsDiscovery's static helpers, so what this prints is what the roster should
contain. A disagreement between this output and `*/network/roster` is a bug in
one of the two, and the output says which half to look at.

THREE THINGS DETERMINE WHETHER METATRON ADMITS THIS ANNOUNCEMENT:

  1. THE PORT MUST NOT BE ONE METATRON SERVES. Admission runs through
     mdnsDiscovery.ours(), which answers "this is me" when the announcing
     address is one of my local addresses AND the port is one of my
     authorities. Running here, on the same host, the address always matches --
     so the port is the only thing keeping this from being ignored as our own
     echo. The default (9999) is deliberately not a metatron port; if you serve
     9999, pass --port something else.

  2. TXT PROPERTIES MUST BE SEPARATE ENTRIES, never one comma-joined string.
     Java's getPropertyString returns everything after the FIRST '=', so
     "route=/,version=0.1" arrives as route=[/,version=0.1]. zeroconf's dict
     gives one entry each, which is correct -- this note is here because the
     Java side has a recovery path for the broken form and you may want to
     exercise it (--txt-joined).

  3. THE ADDRESS MUST BE IPv4. Admission iterates getInet4Addresses(), so an
     IPv6-only announcement is heard and then skipped silently.

CTRL-C IS PART OF THE TEST. Unregistering sends the DNS-SD goodbye that drives
mdnsDiscovery.retract(), so the roster entry should disappear within a second.
Killing it with -9 sends no goodbye, and the entry then sits there looking
dialable until the PTR ages out -- which is the failure mode the deterministic
instance name exists to make recoverable.

    pip install 'zeroconf>=0.38'

    bin/test/mdns_analyzer.py                      # announce and listen
    bin/test/mdns_analyzer.py --mode listen        # watch only
    bin/test/mdns_analyzer.py --mode broadcast     # announce only
    bin/test/mdns_analyzer.py --service _metatron_test._tcp.local.
    bin/test/mdns_analyzer.py --peers 3            # three fake machines at once
"""

import argparse
import re
import signal
import socket
import sys
import time

try:
    from zeroconf import ServiceBrowser, ServiceInfo, ServiceListener, Zeroconf
except ImportError:
    sys.exit("mdns_analyzer needs python-zeroconf:  pip install 'zeroconf>=0.38'")

DEFAULT_SERVICE = "_metatron._tcp.local."
DEFAULT_PORT = 9999
DEFAULT_SCHEME = "ws"
DEFAULT_ROUTE = "/mtron"
DEFAULT_VERSION = "0.1"

# ---------------------------------------------------------------------------
# ports of mdnsDiscovery's static helpers -- keep these in step with the Java
# ---------------------------------------------------------------------------

SCHEME_RE = re.compile(r"^[a-z][a-z0-9+.-]*$")
ROUTE_RE = re.compile(r"^/[^,<>\s]*$")


def scheme(advertised):
    """the advertised scheme, or ws -- a metatron peer answers over websockets"""
    return advertised if advertised and SCHEME_RE.match(advertised) else "ws"


def route(advertised):
    """a route we can put in an address: a path, optionally queried"""
    if advertised is None:
        return "/"
    if ROUTE_RE.match(advertised):
        return advertised
    # an announcer that packed several TXT properties into ONE entry meant the
    # text before the first comma; if even that is not a path, refuse it
    head = advertised.split(",", 1)[0]
    return head if ROUTE_RE.match(head) else "/"


def segment(instance):
    """an instance name as ONE valid uri segment"""
    slug = re.sub(r"[^A-Za-z0-9._-]", "-", instance or "")
    slug = re.sub(r"-{2,}", "-", slug)
    slug = re.sub(r"^-+|-+$", "", slug)
    return slug or "peer"


def machine_key(advertised_scheme, server):
    """the roster KEY: the SRV target, scoped to the surface that advertised it"""
    return "%s://%s" % (scheme(advertised_scheme), segment(server))


def dialable(advertised_scheme, address, port, advertised_route):
    """the roster VALUE: an address usable exactly as it stands"""
    return "%s://%s:%d%s" % (scheme(advertised_scheme), address, port, route(advertised_route))


# ---------------------------------------------------------------------------

def primary_address():
    """
    the LAN address this host would use to reach the world. Opening a UDP socket
    to a routable address sends nothing -- it only asks the routing table which
    interface it would leave by, which is the one worth announcing.
    """
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("8.8.8.8", 80))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


def properties(args):
    if args.txt_joined:
        # THE BROKEN FORM, on purpose: one entry holding several properties, so
        # the Java recovery path (split on the first comma) can be exercised
        return {"route": "%s,version=%s" % (args.route, args.version)}
    return {"route": args.route, "scheme": args.scheme, "version": args.version}


def announcement(args, index):
    """one fake machine: a distinct SRV target and instance name per peer"""
    if 1 == args.peers:
        server = args.host
    else:
        stem = args.host[:-1] if args.host.endswith(".") else args.host
        server = ("%s%d.local." % (stem[:-len(".local")], index + 1)) if stem.endswith(".local") \
            else ("%s%d." % (stem, index + 1))
    port = args.port + index
    instance = "%s-%s-%d" % (server.replace(".local.", ""), args.scheme, port)
    info = ServiceInfo(
        type_=args.service,
        name="%s.%s" % (instance, args.service),
        addresses=[socket.inet_aton(args.address)],
        port=port,
        properties=properties(args),
        server=server,
    )
    return info, server, port


class Analyzer(ServiceListener):
    """prints, for every responder, the roster entry metatron would derive"""

    def __init__(self, mine):
        self.mine = mine

    def add_service(self, zc, type_, name):
        self.report("HEARD", zc, type_, name)

    def update_service(self, zc, type_, name):
        self.report("UPDATED", zc, type_, name)

    def remove_service(self, zc, type_, name):
        mine = " (this is us -- goodbye sent)" if name in self.mine else ""
        print("\n[%s] WITHDRAWN %s%s" % (stamp(), name, mine))
        print("    metatron should drop these addresses from its machine entry")

    def report(self, event, zc, type_, name):
        info = zc.get_service_info(type_, name, timeout=3000)
        if info is None:
            print("\n[%s] %s %s -- no SRV/A yet (unresolved)" % (stamp(), event, name))
            return
        txt = {k.decode("utf-8", "replace"): (v or b"").decode("utf-8", "replace")
               for k, v in (info.properties or {}).items()}
        ipv4 = [a for a in info.parsed_addresses() if ":" not in a]
        mine = "  (this is us)" if name in self.mine else ""
        print("\n[%s] %s %s%s" % (stamp(), event, name, mine))
        print("    server  %s" % info.server)
        print("    port    %d" % info.port)
        print("    txt     %s" % (txt or "(none)"))
        print("    ipv4    %s" % (", ".join(ipv4) or "(none -- metatron will skip this responder)"))
        if not ipv4:
            return
        key = machine_key(txt.get("scheme"), info.server)
        print("    ROSTER  %s => {%s}" % (
            key, ", ".join(dialable(txt.get("scheme"), a, info.port, txt.get("route")) for a in ipv4)))
        if txt.get("route") and not ROUTE_RE.match(txt["route"]):
            print("    NOTE    route %r is not a path; metatron recovers %r"
                  % (txt["route"], route(txt["route"])))
        if key.endswith("."):
            print("    NOTE    the roster key keeps the SRV target's trailing dot -- segment() treats '.' as a\n"
                  "            legal character and trims only '-', so this key matches no address a human types")


def stamp():
    return time.strftime("%H:%M:%S")


def main():
    parser = argparse.ArgumentParser(
        description="announce a fake metatron peer over mDNS and analyze the ones it hears")
    parser.add_argument("--mode", choices=("both", "listen", "broadcast"), default="both")
    parser.add_argument("--service", default=DEFAULT_SERVICE,
                        help="DNS-SD service type (default %(default)s)")
    parser.add_argument("--host", default="fakepeer.local.",
                        help="the SRV target, which becomes the roster key (default %(default)s)")
    parser.add_argument("--address", default=None,
                        help="the IPv4 to advertise (default: this host's primary LAN address)")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT,
                        help="MUST NOT be a port metatron serves, or it reads as our own echo "
                             "(default %(default)s)")
    parser.add_argument("--scheme", default=DEFAULT_SCHEME)
    parser.add_argument("--route", default=DEFAULT_ROUTE)
    parser.add_argument("--version", default=DEFAULT_VERSION)
    parser.add_argument("--peers", type=int, default=1,
                        help="announce N fake machines, ports counting up from --port")
    parser.add_argument("--txt-joined", action="store_true",
                        help="pack the TXT into ONE entry, to exercise the Java recovery path")
    parser.add_argument("--duration", type=int, default=0,
                        help="seconds to run, 0 for until interrupted (default %(default)s)")
    args = parser.parse_args()

    if not args.service.endswith("."):
        args.service += "."
    if not args.host.endswith("."):
        args.host += "."
    if args.address is None:
        args.address = primary_address()

    zc = Zeroconf()
    registered = []
    mine = set()

    if args.mode in ("both", "broadcast"):
        for index in range(max(1, args.peers)):
            info, server, port = announcement(args, index)
            zc.register_service(info)
            registered.append(info)
            mine.add(info.name)
            print("[%s] ANNOUNCING %s" % (stamp(), info.name))
            print("    as      %s => {%s}" % (
                machine_key(args.scheme, server),
                dialable(args.scheme, args.address, port, args.route)))
        print("\n    if metatron serves any of those ports, it will treat this as its own echo\n"
              "    and ignore it -- check `*/network/roster` and pass --port if nothing lands\n")

    browser = None
    if args.mode in ("both", "listen"):
        browser = ServiceBrowser(zc, args.service, Analyzer(mine))
        print("[%s] BROWSING %s -- ctrl-c to withdraw and exit\n" % (stamp(), args.service))

    stop = {"now": False}
    signal.signal(signal.SIGINT, lambda *_: stop.update(now=True))
    signal.signal(signal.SIGTERM, lambda *_: stop.update(now=True))

    endTime = time.time() + args.duration if 0 < args.duration else None
    try:
        while not stop["now"] and (endTime is None or time.time() < endTime):
            time.sleep(0.2)
    finally:
        print("\n[%s] withdrawing %d service(s) -- this is the goodbye that drives retract()"
              % (stamp(), len(registered)))
        for info in registered:
            try:
                zc.unregister_service(info)
            except Exception as e:
                print("    could not unregister %s (%s)" % (info.name, e))
        if browser is not None:
            browser.cancel()
        zc.close()
        print("[%s] done" % stamp())


if __name__ == "__main__":
    main()
