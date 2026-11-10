package com.olivamore.api.analytics.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface AnalyticsReportRepository extends JpaRepository<AnalyticsReport, Long> {

	List<AnalyticsReport> findByUserIdOrderByUpdatedAtDesc(long userId);

}
