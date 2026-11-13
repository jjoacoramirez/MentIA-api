package com.mentia.ai;

/** Se usa cuando la IA está apagada o no hay key: toda llamada falla con {@link AiUnavailableException}. */
public class DisabledAiClient implements AiClient {

    private final String reason;

    public DisabledAiClient(String reason) {
        this.reason = reason;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        throw new AiUnavailableException(AiUnavailableException.Reason.DISABLED, reason);
    }

    @Override
    public boolean available() {
        return false;
    }
}
