package com.example.classselector.client

import com.example.classselector.network.ClassSelectorNetwork
import com.example.classselector.network.RequestRespawnVotingSyncPacket
import com.example.classselector.network.SuggestRespawnHubPacket
import com.example.classselector.network.VoteRespawnHubPacket
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class RespawnVotingScreen : Screen(Component.literal("Respawn Hub Voting")) {
    private var selectedProposalIndex: Int = 0
    private val proposalButtons: MutableList<Button> = mutableListOf()
    private var voteButton: Button? = null
    private var suggestButton: Button? = null

    override fun init() {
        ClassSelectorNetwork.CHANNEL.sendToServer(RequestRespawnVotingSyncPacket())
        rebuild()
    }

    override fun tick() {
        super.tick()
        selectedProposalIndex = selectedProposalIndex.coerceIn(0, (RespawnVotingState.snapshot.proposals.lastIndex).coerceAtLeast(0))
    }

    private fun rebuild() {
        clearWidgets()
        proposalButtons.clear()

        val snapshot = RespawnVotingState.snapshot
        val layoutWidth = 540
        val layoutLeft = (width - layoutWidth) / 2
        val listLeft = layoutLeft
        val infoLeft = layoutLeft + 220
        val top = 36

        snapshot.proposals.forEachIndexed { index, proposal ->
            val y = top + index * 24
            val button = addRenderableWidget(
                Button.builder(Component.literal("#${proposal.id}  ${proposal.votes}v")) {
                    selectedProposalIndex = index
                    refreshButtonState()
                }.pos(listLeft, y).size(200, 20).build()
            )
            proposalButtons += button
        }

        suggestButton = addRenderableWidget(
            Button.builder(Component.literal("Suggest Current Location")) {
                ClassSelectorNetwork.CHANNEL.sendToServer(SuggestRespawnHubPacket())
                ClassSelectorNetwork.CHANNEL.sendToServer(RequestRespawnVotingSyncPacket())
            }.pos(infoLeft, height - 72).size(170, 20).build()
        )

        voteButton = addRenderableWidget(
            Button.builder(Component.literal("Vote For Selected")) {
                val selectedProposal = RespawnVotingState.snapshot.proposals.getOrNull(selectedProposalIndex) ?: return@builder
                ClassSelectorNetwork.CHANNEL.sendToServer(VoteRespawnHubPacket(selectedProposal.id))
                ClassSelectorNetwork.CHANNEL.sendToServer(RequestRespawnVotingSyncPacket())
            }.pos(infoLeft + 180, height - 72).size(140, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Refresh")) {
                ClassSelectorNetwork.CHANNEL.sendToServer(RequestRespawnVotingSyncPacket())
            }.pos(infoLeft, height - 42).size(100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Back")) {
                onClose()
            }.pos(infoLeft + 110, height - 42).size(80, 20).build()
        )

        refreshButtonState()
    }

    private fun refreshButtonState() {
        val canVote = RespawnVotingState.snapshot.canVote
        val hasVoted = RespawnVotingState.snapshot.currentVoteProposalId != null
        val selectedProposalId = RespawnVotingState.snapshot.proposals.getOrNull(selectedProposalIndex)?.id
        proposalButtons.forEachIndexed { index, button ->
            button.active = canVote && index != selectedProposalIndex
        }
        voteButton?.active = canVote &&
            RespawnVotingState.snapshot.proposals.isNotEmpty() &&
            selectedProposalId != null &&
            selectedProposalId != RespawnVotingState.snapshot.currentVoteProposalId
        suggestButton?.active = canVote && !hasVoted
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val snapshot = RespawnVotingState.snapshot
        if (proposalButtons.size != snapshot.proposals.size) {
            rebuild()
        } else {
            proposalButtons.forEachIndexed { index, button ->
                val proposal = snapshot.proposals[index]
                button.message = Component.literal("#${proposal.id}  ${proposal.votes}v")
            }
        }

        renderBackground(gui)
        gui.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF)

        val layoutWidth = 540
        val layoutLeft = (width - layoutWidth) / 2
        val infoLeft = layoutLeft + 220
        val panelTop = 36
        val panelBottom = height - 88
        gui.fill(infoLeft - 8, panelTop - 8, infoLeft + 320, panelBottom, 0x66000000)

        gui.drawWordWrap(
            font,
            Component.literal("Spectators can survey the world, suggest their current location as a respawn hub, and vote on the best start area without using chat commands."),
            infoLeft,
            panelTop,
            300,
            0xE6D28C
        )

        gui.drawString(font, Component.literal("Enabled: ${snapshot.enabled}"), infoLeft, panelTop + 54, 0xFFFFFF)
        gui.drawString(font, Component.literal("Voting access: ${if (snapshot.canVote) "active" else "closed"}"), infoLeft, panelTop + 68, if (snapshot.canVote) 0x9FE3A0 else 0xC8A0A0)
        gui.drawString(font, Component.literal("Eligible spectators: ${snapshot.eligibleSpectators}"), infoLeft, panelTop + 82, 0xFFFFFF)
        gui.drawString(font, Component.literal("Your vote: ${snapshot.currentVoteProposalId?.let { "#$it" } ?: "none"}"), infoLeft, panelTop + 96, 0xB0D7FF)

        minecraft?.player?.let { player ->
            val dim = player.level().dimension().location().toString()
            gui.drawString(font, Component.literal("Current survey spot"), infoLeft, panelTop + 122, 0xFFFFFF)
            gui.drawString(font, Component.literal(dim), infoLeft, panelTop + 136, 0xA0A0A0)
            gui.drawString(font, Component.literal("${player.blockX} ${player.blockY} ${player.blockZ}"), infoLeft, panelTop + 150, 0xA0A0A0)
        }

        val selected = snapshot.proposals.getOrNull(selectedProposalIndex)
        gui.drawString(font, Component.literal("Selected proposal"), infoLeft, panelTop + 186, 0xFFFFFF)
        if (selected == null) {
            gui.drawString(font, Component.literal(if (snapshot.canVote) "No proposals yet." else "Voting is not available for you right now."), infoLeft, panelTop + 200, 0x999999)
        } else {
            gui.drawString(font, Component.literal("#${selected.id} by ${selected.suggesterName}"), infoLeft, panelTop + 200, 0xFFFFFF)
            gui.drawString(font, Component.literal(selected.hub.dim), infoLeft, panelTop + 214, 0xA0A0A0)
            gui.drawString(font, Component.literal("${selected.hub.x} ${selected.hub.y} ${selected.hub.z}"), infoLeft, panelTop + 228, 0xA0A0A0)
            gui.drawString(font, Component.literal("Votes: ${selected.votes}"), infoLeft, panelTop + 242, 0x9FE3A0)
            gui.drawWordWrap(
                font,
                Component.literal(
                    if (snapshot.canVote)
                        "Press Esc to keep surveying, then reopen this screen with V when you want to suggest or change your vote."
                    else
                        "Spawn locations are already finalized for this world. Late joiners still choose a class, but they do not participate in voting."
                ),
                infoLeft,
                panelTop + 266,
                300,
                0xCCCCCC
            )
        }

        super.render(gui, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false
}
