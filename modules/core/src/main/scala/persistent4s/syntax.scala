/*
 * Copyright 2026 persistent4s
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package persistent4s

import cats.effect.Concurrent
import cats.syntax.all.*

object syntax:

  extension [C, S, E](handler: CommandHandler[C, S, E])

    /** Execute a command using this handler and the given event store. */
    def run[F[_]: Concurrent](command: C)(using eventStore: EventStore[F, E]): F[Unit] =
      for
        tags      <- Concurrent[F].pure(handler.tags(command))
        envelopes <- eventStore.read(handler.eventTypes.getOrElse(Set.empty).toList, tags).compile.toList
        state      = envelopes.foldLeft(handler.initial)((s, env) => handler.evolve(s, env.payload))
        index      = envelopes.lastOption.map(_.metadata.globalPosition).getOrElse(0L)
        _         <- handler.validate(state, command)
        decided    = handler.decide(state, command)
        events     = decided.map((tags, event) => (tags, event.getClass.getSimpleName, event))
        _         <- eventStore.append(index, events)
      yield ()
