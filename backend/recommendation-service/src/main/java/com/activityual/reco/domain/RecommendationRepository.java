package com.activityual.reco.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {
    List<Recommendation> findByUserIdAndDismissedFalseAndAcceptedFalseOrderByConfidenceDesc(UUID userId);

    @Modifying
    @Query("delete from Recommendation r where r.userId = :uid and r.activityId = :aid and r.accepted = false and r.dismissed = false")
    void deleteOpenForActivity(@Param("uid") UUID uid, @Param("aid") UUID aid);
}

