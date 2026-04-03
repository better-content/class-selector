package com.example.classselector.client

import com.example.classselector.respawn.RespawnHub

data class RespawnVotingProposalView(
    val id: Int,
    val hub: RespawnHub,
    val suggesterName: String,
    val votes: Int
)

data class RespawnVotingSnapshot(
    val enabled: Boolean,
    val canVote: Boolean,
    val eligibleSpectators: Int,
    val currentVoteProposalId: Int?,
    val proposals: List<RespawnVotingProposalView>
)

object RespawnVotingState {
    var snapshot: RespawnVotingSnapshot = RespawnVotingSnapshot(
        enabled = false,
        canVote = false,
        eligibleSpectators = 0,
        currentVoteProposalId = null,
        proposals = emptyList()
    )
}
