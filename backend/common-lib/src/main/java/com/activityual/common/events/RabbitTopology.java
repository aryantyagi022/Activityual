package com.activityual.common.events;

public final class RabbitTopology {
    public static final String EXCHANGE = "activity.events";
    public static final String ROUTING_KEY_LOGGED = "activity.logged";

    public static final String QUEUE_ANALYTICS = "analytics.q";
    public static final String QUEUE_COACH     = "coach.q";
    public static final String QUEUE_RECO      = "reco.q";
    public static final String QUEUE_NOTIF     = "notif.q";

    private RabbitTopology() {}
}

