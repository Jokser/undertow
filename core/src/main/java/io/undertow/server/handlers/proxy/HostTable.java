package io.undertow.server.handlers.proxy;

import io.undertow.UndertowMessages;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.PathMatcher;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Class that maintains a table of remote hosts that this proxy knows about.
 *
 * Basically this maps a virtual host + context path pair to a set of hosts.
 *
 * Note that this class does not have any knowledge of connection pooling
 *
 * @author Stuart Douglas
 */
public class HostTable<H> {

    private final Map<H, Set<Target>> hosts = new CopyOnWriteMap<H, Set<Target>>();
    private final Map<String, PathMatcher<Set<H>>> targets = new CopyOnWriteMap<String, PathMatcher<Set<H>>>();

    public synchronized HostTable addHost(H host) {
        if(hosts.containsKey(host)) {
            throw UndertowMessages.MESSAGES.hostAlreadyRegistered(host);
        }
        hosts.put(host, new CopyOnWriteArraySet<Target>());
        return this;
    }

    public synchronized HostTable removeHost(H host) {
        Set<Target> targets = hosts.remove(host);
        for(Target target : targets) {
            removeRoute(host, target.virtualHost, target.contextPath);
        }
        return this;
    }

    public synchronized HostTable addRoute(H host, String virtualHost, String contextPath) {
        Set<Target> hostData = hosts.get(host);
        if(hostData == null) {
            throw UndertowMessages.MESSAGES.hostHasNotBeenRegistered(host);
        }
        hostData.add(new Target(virtualHost, contextPath));
        PathMatcher<Set<H>> paths = targets.get(virtualHost);
        if(paths == null) {
            paths = new PathMatcher<Set<H>>();
            targets.put(virtualHost, paths);
        }
        Set<H> hostSet = paths.getPrefixPath(contextPath);
        if(hostSet == null) {
            hostSet = new CopyOnWriteArraySet<H>();
            paths.addPrefixPath(contextPath, hostSet);
        }
        hostSet.add(host);
        return this;
    }

    public synchronized HostTable removeRoute(H host, String virtualHost, String contextPath) {

        Set<Target> hostData = hosts.get(host);
        if(hostData != null) {
            hostData.remove(new Target(virtualHost, contextPath));
        }
        PathMatcher<Set<H>> paths = targets.get(virtualHost);
        if(paths == null) {
            return this;
        }
        Set<H> hostSet = paths.getPrefixPath(contextPath);
        if(hostSet == null) {
            return this;
        }
        hostSet.remove(host);
        if(hostSet.isEmpty()) {
            paths.removePrefixPath(contextPath);
        }
        return this;
    }

    public Set<H> getHostsForTarget(final String hostName, final String path) {
        PathMatcher<Set<H>> matcher = targets.get(hostName);
        if(matcher == null) {
            return null;
        }
        return matcher.match(path).getValue();
    }

    private static final class Target {
        final String virtualHost;
        final String contextPath;

        private Target(String virtualHost, String contextPath) {
            this.virtualHost = virtualHost;
            this.contextPath = contextPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Target target = (Target) o;

            if (contextPath != null ? !contextPath.equals(target.contextPath) : target.contextPath != null)
                return false;
            if (virtualHost != null ? !virtualHost.equals(target.virtualHost) : target.virtualHost != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = virtualHost != null ? virtualHost.hashCode() : 0;
            result = 31 * result + (contextPath != null ? contextPath.hashCode() : 0);
            return result;
        }
    }

}
