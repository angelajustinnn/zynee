package com.zynee.zynee.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zynee.zynee.model.MusicTrack;

public interface MusicTrackRepository extends JpaRepository<MusicTrack, Integer> {
}
