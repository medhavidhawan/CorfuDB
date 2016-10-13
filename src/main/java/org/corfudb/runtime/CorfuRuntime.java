package org.corfudb.runtime;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.clients.IClientRouter;
import org.corfudb.runtime.clients.LayoutClient;
import org.corfudb.runtime.clients.LogUnitClient;
import org.corfudb.runtime.clients.NettyClientRouter;
import org.corfudb.runtime.clients.SequencerClient;
import org.corfudb.runtime.view.AddressSpaceView;
import org.corfudb.runtime.view.Layout;
import org.corfudb.runtime.view.LayoutView;
import org.corfudb.runtime.view.ObjectsView;
import org.corfudb.runtime.view.SequencerView;
import org.corfudb.runtime.view.StreamsView;
import org.corfudb.util.GitRepositoryState;
import org.corfudb.util.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by mwei on 12/9/15.
 */
@Slf4j
public class CorfuRuntime {

    /**
     * A view of the layout service in the Corfu server instance.
     */
    @Getter(lazy = true)
    private final LayoutView layoutView = new LayoutView(this);
    /**
     * A view of the sequencer server in the Corfu server instance.
     */
    @Getter(lazy = true)
    private final SequencerView sequencerView = new SequencerView(this);
    /**
     * A view of the address space in the Corfu server instance.
     */
    @Getter(lazy = true)
    private final AddressSpaceView addressSpaceView = new AddressSpaceView(this);
    /**
     * A view of streams in the Corfu server instance.
     */
    @Getter(lazy = true)
    private final StreamsView streamsView = new StreamsView(this);

    //region Address Space Options
    /**
     * Views of objects in the Corfu server instance.
     */
    @Getter(lazy = true)
    private final ObjectsView objectsView = new ObjectsView(this);
    /**
     * A list of known layout servers.
     */
    public List<String> layoutServers;

    //endregion Address Space Options
    /**
     * A map of routers, representing nodes.
     */
    public Map<String, IClientRouter> nodeRouters;
    /**
     * A completable future containing a layout, when completed.
     */
    public volatile CompletableFuture<Layout> layout;
    /**
     * The rate in seconds to retry accessing a layout, in case of a failure.
     */
    public int retryRate;
    /**
     * Whether or not to disable the cache.
     */
    @Getter
    public boolean cacheDisabled = false;
    /**
     * The maximum size of the cache, in bytes.
     */
    @Getter
    public int maxCacheSize = 100_000_000;
    /**
     * Whether or not to disable backpointers.
     */
    @Getter
    public boolean backpointersDisabled = false;

    /**
     * When set, overrides the default getRouterFunction. Used by the testing
     * framework to ensure the default routers used are for testing.
     */
    public static BiFunction<CorfuRuntime, String, IClientRouter> overrideGetRouterFunction = null;

    /**
     * A function to handle getting routers. Used by test framework to inject
     * a test router. Can also be used to provide alternative logic for obtaining
     * a router.
     */
    @Getter
    @Setter
    public Function<String, IClientRouter> getRouterFunction = overrideGetRouterFunction != null ?
            (address) -> overrideGetRouterFunction.apply(this, address) : (address) -> {

        // Return an existing router if we already have one.
        if (nodeRouters.containsKey(address)) {
            return nodeRouters.get(address);
        }
        // Parse the string in host:port format.
        String host = address.split(":")[0];
        Integer port = Integer.parseInt(address.split(":")[1]);
        // Generate a new router, start it and add it to the table.
        NettyClientRouter router = new NettyClientRouter(host, port);
        log.debug("Connecting to new router {}:{}", host, port);
        try {
            router.addClient(new LayoutClient())
                    .addClient(new SequencerClient())
                    .addClient(new LogUnitClient())
                    .start();
            nodeRouters.put(address, router);
        } catch (Exception e) {
            log.warn("Error connecting to router", e);
        }
        return router;
    };

    public CorfuRuntime() {
        layoutServers = new ArrayList<>();
        nodeRouters = new ConcurrentHashMap<>();
        retryRate = 5;
        log.debug("Corfu runtime version {} initialized.", getVersionString());
    }

    /**
     * Stop all routers associated with this runtime & disconnect them.
     */
    public void stop() {
        stop(false);
    }

    public void stop(boolean shutdown_p) {
        for (IClientRouter r: nodeRouters.values()) {
            r.stop(shutdown_p);
        }
        if (shutdown_p) {
            // N.B. An icky side-effect of this clobbering is leaking
            // Pthreads, namely the Netty client-side worker threads.
            nodeRouters = new ConcurrentHashMap<>();
        }
    }

    /**
     * Parse a configuration string and get a CorfuRuntime.
     *
     * @param configurationString The configuration string to parse.
     */
    public CorfuRuntime(String configurationString) {
        this();
        this.parseConfigurationString(configurationString);
    }

    /**
     * Get a UUID for a named stream.
     *
     * @param string The name of the stream.
     * @return The ID of the stream.
     */
    public static UUID getStreamID(String string) {
        return UUID.nameUUIDFromBytes(string.getBytes());
    }

    public static String getVersionString() {
        if (Version.getVersionString().contains("SNAPSHOT") || Version.getVersionString().contains("source")) {
            return Version.getVersionString() + "(" + GitRepositoryState.getRepositoryState().commitIdAbbrev + ")";
        }
        return Version.getVersionString();
    }

    /**
     * Whether or not to disable backpointers
     *
     * @param disable True, if the cache should be disabled, false otherwise.
     * @return A CorfuRuntime to support chaining.
     */
    public CorfuRuntime setBackpointersDisabled(boolean disable) {
        this.backpointersDisabled = disable;
        return this;
    }

    /**
     * Whether or not to disable the cache
     *
     * @param disable True, if the cache should be disabled, false otherwise.
     * @return A CorfuRuntime to support chaining.
     */
    public CorfuRuntime setCacheDisabled(boolean disable) {
        this.cacheDisabled = disable;
        return this;
    }

    public CorfuRuntime setTransactionLogging(boolean enable) {
        this.getObjectsView().setTransactionLogging(enable);
        return this;
    }

    /**
     * Parse a configuration string and get a CorfuRuntime.
     *
     * @param configurationString The configuration string to parse.
     * @return A CorfuRuntime Configured based on the configuration string.
     */
    public CorfuRuntime parseConfigurationString(String configurationString) {
        // Parse comma sep. list.
        layoutServers = Pattern.compile(",")
                .splitAsStream(configurationString)
                .map(String::trim)
                .collect(Collectors.toList());
        return this;
    }

    /**
     * Add a layout server to the list of servers known by the CorfuRuntime.
     *
     * @param layoutServer A layout server to use.
     * @return A CorfuRuntime, to support the builder pattern.
     */
    public CorfuRuntime addLayoutServer(String layoutServer) {
        layoutServers.add(layoutServer);
        return this;
    }

    /**
     * Get a router, given the address.
     *
     * @param address The address of the router to get.
     * @return The router.
     */
    public IClientRouter getRouter(String address) {
        return getRouterFunction.apply(address);
    }

    /**
     * Invalidate the current layout.
     * If the layout has been previously invalidated and a new layout has not yet been retrieved,
     * this function does nothing.
     */
    public void invalidateLayout() {
        // Is there a pending request to retrieve the layout?
        if (!layout.isDone()) {
            // Don't create a new request for a layout if there is one pending.
            return;
        }
        layout = fetchLayout();
    }


    /**
     * Return a completable future which is guaranteed to contain a layout.
     * This future will continue retrying until it gets a layout. If you need this completable future to fail,
     * you should chain it with a timeout.
     *
     * @return A completable future containing a layout.
     */
    private CompletableFuture<Layout> fetchLayout() {
        return CompletableFuture.<Layout>supplyAsync(() -> {
            while (true) {
                // Iterate through the layout servers, attempting to connect to one
                for (String s : layoutServers) {
                    log.debug("Trying connection to layout server {}", s);
                    try {
                        IClientRouter router = getRouter(s);
                        // Try to get a layout.
                        CompletableFuture<Layout> layoutFuture = router.getClient(LayoutClient.class).getLayout();
                        // Wait for layout
                        Layout l = layoutFuture.get();
                        l.setRuntime(this);
                        // this.layout should only be assigned to the new layout future once it has been
                        // completely constructed and initialized. For example, assigning this.layout = l
                        // before setting the layout's runtime can result in other threads trying to access a layout
                        // with  a null runtime.
                        layout = layoutFuture;
                        l.getAllServers().stream()
                                .map(getRouterFunction)
                                .forEach(x -> x.setEpoch(l.getEpoch()));
                        log.debug("Layout server {} responded with layout {}", s, l);
                        return l;
                    } catch (Exception e) {
                        log.warn("Tried to get layout from {} but failed with exception:", s, e);
                    }
                }
                log.warn("Couldn't connect to any layout servers, retrying in {}s.", retryRate);
                try {
                    Thread.sleep(retryRate * 1000);
                } catch (InterruptedException e) {
                }
            }
        });
    }

    /**
     * Connect to the Corfu server instance.
     * When this function returns, the Corfu server is ready to be accessed.
     */
    public synchronized CorfuRuntime connect() {
        if (layout == null) {
            log.info("Connecting to Corfu server instance, layout servers={}", layoutServers);
            // Fetch the current layout and save the future.
            layout = fetchLayout();
            try {
                layout.get();
            } catch (Exception e) {
                // A serious error occurred trying to connect to the Corfu instance.
                log.error("Fatal error connecting to Corfu server instance.", e);
                throw new RuntimeException(e);
            }
        }
        return this;
    }
}
