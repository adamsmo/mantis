package io.iohk.ethereum.sync

import io.iohk.ethereum.FlatSpecBase
import io.iohk.ethereum.sync.util.FakePeerRegularSync
import io.iohk.ethereum.sync.util.SyncUtils.identityUpdate
import monix.execution.Scheduler
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class RegularSyncItSpec extends FlatSpecBase with Matchers with BeforeAndAfter {
  implicit val testScheduler = Scheduler.fixedPool("test", 16)

  it should "should sync blockchain with same best block" in customTestCaseResourceM(FakePeerRegularSync.start2FakePeersRes()) {
    case (peer1, peer2) =>
      val blockNumer: Int = 2000
      for {
        _ <- peer2.importBlocksUntil(blockNumer)(identityUpdate)
        _ <- peer1.connectToPeers(Set(peer2.node))
        _ <- peer1.start.delayExecution(50.milliseconds)
        _ <- peer2.broadcastBlock()(identityUpdate).delayExecution(500.milliseconds)
        _ <- peer1.waitForRegularSyncLoadLastBlock(blockNumer)
      } yield {
        assert(peer1.bl.getBestBlockNumber() == peer2.bl.getBestBlockNumber())
      }
  }

  it should "should sync blockchain progressing forward in the same time" in customTestCaseResourceM(FakePeerRegularSync.start2FakePeersRes()) {
    case (peer1, peer2) =>
      val blockNumer: Int = 2000
      for {
        _ <- peer2.start.delayExecution(50.milliseconds)
        _ <- peer2.importBlocksUntil(blockNumer)(identityUpdate)
        _ <- peer1.connectToPeers(Set(peer2.node))
        _ <- peer1.start.delayExecution(500.milliseconds)
        _ <- peer2.mineNewBlock()(identityUpdate).delayExecution(50.milliseconds)
        _ <- peer1.waitForRegularSyncLoadLastBlock(blockNumer + 1)
      } yield {
        assert(peer1.bl.getBestBlockNumber() == peer2.bl.getBestBlockNumber())
      }
  }

  it should "should sync peers with divergent chains will be forced to resolve branches"in customTestCaseResourceM(FakePeerRegularSync.start2FakePeersRes()) {
    case (peer1, peer2) =>
      val blockNumer: Int = 2000
      for {
        _ <- peer2.importBlocksUntil(blockNumer)(identityUpdate)
        _ <- peer2.start.delayExecution(50.milliseconds)
        _ <- peer1.importBlocksUntil(blockNumer)(identityUpdate)
        _ <- peer1.start.delayExecution(50.milliseconds)
        _ <- peer2.mineNewBlock(10)(identityUpdate).delayExecution(500.milliseconds)
        _ <- peer2.mineNewBlock(10)(identityUpdate).delayExecution(500.milliseconds)
        _ <- peer2.mineNewBlock(10)(identityUpdate).delayExecution(500.milliseconds)
        _ <- peer2.waitForRegularSyncLoadLastBlock(blockNumer + 3)
        _ <- peer1.mineNewBlock()(identityUpdate).delayExecution(500.milliseconds)
        _ <- peer1.waitForRegularSyncLoadLastBlock(blockNumer + 1)
        _ <- peer1.connectToPeers(Set(peer2.node)).delayExecution(500.milliseconds)
        _ <- peer1.waitForRegularSyncLoadLastBlock(blockNumer + 3)
        _ <- peer2.waitForRegularSyncLoadLastBlock(blockNumer + 3)
      } yield {
        assert(peer1.bl.getBestBlock().number == peer2.bl.getBestBlock().number)
        (peer1.bl.getBlockByNumber(blockNumer + 1), peer1.bl.getBlockByNumber(blockNumer + 1)) match {
          case (Some(blockP1), Some(blockP2)) => assert(blockP1.header.difficulty == blockP2.header.difficulty)
          case (_ , _) => fail("invalid difficulty validation")
        }
      }
  }

}
