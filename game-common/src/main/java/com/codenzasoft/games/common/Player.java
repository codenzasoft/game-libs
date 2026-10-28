package com.codenzasoft.games.common;

import java.util.UUID;

/**
 * A player in a {@link Game}.
 *
 * @param id unique identifier for this player
 * @param name display name used for game-engine seat lookups and STOMP routing
 */
public record Player(UUID id, String name) {}
