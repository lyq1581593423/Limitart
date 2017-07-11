package com.limitart.net.binary.distributed.handler;

import com.limitart.net.binary.distributed.InnerSlaveServer;
import com.limitart.net.binary.distributed.message.ResServerJoinMaster2SlaveMessage;
import com.limitart.net.binary.handler.IHandler;

public class ResServerJoinMaster2SlaveHandler implements IHandler<ResServerJoinMaster2SlaveMessage> {

	@Override
	public void handle(ResServerJoinMaster2SlaveMessage msg) {
		((InnerSlaveServer) msg.getExtra()).ResServerJoinMaster2Slave(msg);
	}

}
