package chabernac.p2pclient.gui.action;

import chabernac.p2pclient.gui.ChatMediator;

public class ReplyAllCommand extends AbstractMediatorCommand {
  private static final long serialVersionUID = -610125396515596673L;

  public ReplyAllCommand(ChatMediator anMediator) {
    super(anMediator);
  }

  @Override
  public void execute() {
    myMediator.selectReplyAllUsers();
  }

}
