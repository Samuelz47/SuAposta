package com.suaposta.messaging.contract;

public final class MessagingConstants {

    public static final String BETTING_EVENTS_EXCHANGE = "betting.events";
    public static final String ANALYTICS_BETTING_EVENTS_QUEUE = "analytics.betting-events.queue";
    public static final String BET_CREATED_ROUTING_KEY = "bet.created";
    public static final String BET_UPDATED_ROUTING_KEY = "bet.updated";
    public static final String BET_SETTLED_ROUTING_KEY = "bet.settled";
    public static final String BETTING_SERVICE_PRODUCER = "betting-service";
    public static final int VERSION_ONE = 1;

    private MessagingConstants() {
    }
}
