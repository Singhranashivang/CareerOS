package com.careeros.backend.github.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GithubTreeResponse {

    private List<TreeNode> tree;

}