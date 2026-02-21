package com.live_commerce.user.application.port.in;

import com.live_commerce.user.application.dto.command.ReissueTokenCommand;
import com.live_commerce.user.application.dto.result.TokenReissueResult;

public interface ReissueTokenUseCase {
	TokenReissueResult reissue(ReissueTokenCommand command);
}
