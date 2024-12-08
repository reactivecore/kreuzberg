package kreuzberg.examples.showcase

import io.circe.Codec

/** Initialization data sent from Server to client. */
case class InitData(
    code: String
) derives Codec
