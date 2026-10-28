package com.codenzasoft.games.common;

import java.util.UUID;

/**
 * A player in a {@link Game}.
 *
 * @param id unique identifier for this player
 * @param name display name for this player
 */
public record Player(UUID id, String name) {}
