package com.dotteam.onceuponatown.menu;

import com.dotteam.onceuponatown.entity.Citizen;
import com.dotteam.onceuponatown.trade.BuyDeal;
import com.dotteam.onceuponatown.trade.SellDeal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ClientSideInteractingCitizen implements InteractableCitizen {
    private Citizen citizen;
    private Player interactingPlayer;
    private List<BuyDeal> buyDeals;
    private List<SellDeal> sellDeals;

    private ClientSideInteractingCitizen(Builder builder) {
        this.citizen = builder.citizen;
        this.interactingPlayer = builder.interactingPlayer;
        this.buyDeals = builder.buyDeals;
        this.sellDeals = builder.sellDeals;
    }

    public static class Builder {
        private Citizen citizen;
        private Player interactingPlayer;
        private List<BuyDeal> buyDeals;
        private List<SellDeal> sellDeals;

        public Builder(Citizen citizen, Player interactingPlayer) {
            this.citizen = citizen;
            this.interactingPlayer = interactingPlayer;
        }

        public Builder buyDeals(List<BuyDeal> deals) {
            this.buyDeals = deals;
            return this;
        }

        public Builder sellDeals(List<SellDeal> deals) {
            this.sellDeals = deals;
            return this;
        }

        public Builder quests() {
            return this;
        }

        public ClientSideInteractingCitizen build() {
            return new ClientSideInteractingCitizen(this);
        }
    }

    @Nullable
    @Override
    public Player getInteractingPlayer() {
        return this.interactingPlayer;
    }

    @Override
    public void setInteractingPlayer(@Nullable Player player) {
        this.interactingPlayer = player;
    }

    public List<BuyDeal> getBuyDeals() {
        return this.buyDeals;
    }

    public List<SellDeal> getSellDeals() {
        return this.sellDeals;
    }



    @Override
    public void notifyDealMade(BuyDeal deal) {
        //deals.update(deal)
    }

    @Override
    public Citizen getCitizen() {
        return this.citizen;
    }
}
