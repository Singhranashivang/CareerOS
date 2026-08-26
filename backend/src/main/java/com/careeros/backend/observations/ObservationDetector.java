package com.careeros.backend.observations;

import com.careeros.backend.user.User;

import java.util.List;

/** One detection rule. Returns zero or more observations — never filler when nothing qualifies. */
public interface ObservationDetector {

    List<Observation> detect(User user);
}
