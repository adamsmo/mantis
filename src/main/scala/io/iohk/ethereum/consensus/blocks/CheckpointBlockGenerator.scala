package io.iohk.ethereum.consensus.blocks

import akka.util.ByteString
import io.iohk.ethereum.consensus.difficulty.DifficultyCalculator
import io.iohk.ethereum.domain.BlockHeader.HeaderExtraFields.HefPostEcip1097
import io.iohk.ethereum.domain._
import io.iohk.ethereum.ledger.BloomFilter

class CheckpointBlockGenerator(
    difficultyCalculator: DifficultyCalculator
) {

  def generate(parent: Block, checkpoint: Checkpoint): Block = {
    val blockNumber = parent.number + 1
    // we are using a predictable value for timestamp so that each federation node generates identical block
    // see ETCM-173
    val timestamp = parent.header.unixTimestamp + 1

    val header = BlockHeader(
      parentHash = parent.hash,
      ommersHash = BlockHeader.EmptyOmmers,
      beneficiary = BlockHeader.EmptyBeneficiary,
      difficulty = difficultyCalculator.calculateDifficulty(blockNumber, timestamp, parent.header),
      number = blockNumber,
      gasLimit = parent.header.gasLimit,
      unixTimestamp = timestamp,
      extraData = ByteString.empty,
      stateRoot = parent.header.stateRoot,
      transactionsRoot = BlockHeader.EmptyMpt,
      receiptsRoot = BlockHeader.EmptyMpt,
      logsBloom = BloomFilter.EmptyBloomFilter,
      gasUsed = UInt256.Zero,
      mixHash = ByteString.empty,
      nonce = ByteString.empty,
      extraFields = HefPostEcip1097(false, Some(checkpoint))
    )

    Block(header, BlockBody.empty)
  }
}