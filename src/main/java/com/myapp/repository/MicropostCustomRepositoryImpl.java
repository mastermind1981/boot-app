package com.myapp.repository;

import com.myapp.domain.*;
import com.myapp.dto.PageParams;
import com.myapp.dto.PostDTO;
import com.myapp.dto.UserStats;
import com.myapp.repository.helper.UserStatsQueryHelper;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class MicropostCustomRepositoryImpl implements MicropostCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public MicropostCustomRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<PostDTO> findAsFeed(User user, PageParams pageParams) {
        final QMicropost qMicropost = QMicropost.micropost;
        final QRelationship qRelationship = QRelationship.relationship;

        final ConstructorExpression<UserStats> userStatsExpression =
                UserStatsQueryHelper.userStatsExpression(qMicropost.user, user);
        final JPQLQuery<Relationship> relationshipSubQuery = JPAExpressions
                .selectFrom(qRelationship)
                .where(qRelationship.follower.eq(user)
                        .and(qRelationship.followed.eq(qMicropost.user))
                );
        return queryFactory.select(qMicropost, qMicropost.user, userStatsExpression)
                .from(qMicropost)
                .innerJoin(qMicropost.user)
                .where((qMicropost.user.eq(user).or(relationshipSubQuery.exists()))
                        .and(pageParams.getSinceId().map(qMicropost.id::gt).orElse(null))
                        .and(pageParams.getMaxId().map(qMicropost.id::lt).orElse(null))
                )
                .orderBy(qMicropost.id.desc())
                .limit(pageParams.getCount())
                .fetch()
                .stream()
                .map(row -> {
                    final Micropost micropost = row.get(qMicropost);
                    final UserStats userStats = row.get(userStatsExpression);
                    assert micropost != null;
                    final Boolean isMyPost = (micropost.getUser().equals(user));
                    return PostDTO.newInstance(micropost, userStats, isMyPost);

                })
                .collect(Collectors.toList());
    }

    @Override
    public List<PostDTO> findByUser(User user, Boolean isMyself, PageParams pageParams) {
        final QMicropost qMicropost = QMicropost.micropost;
        return queryFactory.selectFrom(qMicropost)
                .where(qMicropost.user.eq(user)
                        .and(pageParams.getSinceId().map(qMicropost.id::gt).orElse(null))
                        .and(pageParams.getMaxId().map(qMicropost.id::lt).orElse(null))
                )
                .orderBy(qMicropost.id.desc())
                .limit(pageParams.getCount())
                .fetch()
                .stream()
                .map(post -> PostDTO.newInstance(post, isMyself))
                .collect(Collectors.toList());
    }

}