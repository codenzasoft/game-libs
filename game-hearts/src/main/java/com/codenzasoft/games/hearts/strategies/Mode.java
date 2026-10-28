package com.codenzasoft.games.hearts.strategies;

/** The strategic mode an {@link AdaptiveStrategy} bot is currently operating in. */
public enum Mode {
  /** Play defensively: avoid taking point cards. */
  DEFENSIVE,
  /** Attempt to shoot the moon: accumulate all 26 points. */
  OFFENSIVE,
  /** Sabotage an opponent who is shooting the moon. */
  SABOTEUR
}
