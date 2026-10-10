package com.wind.mybatis.flex;

import com.wind.core.resources.TreeResources;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class MybatisTreeObjectHelperTests {

    @Test
    void testFlattenTreePreservesPreorderAndOriginalTree() {
        TestTreeResources grandchild = new TestTreeResources(3L, "孙节点", 2L, "子节点", 2, null);
        List<TestTreeResources> grandchildren = List.of(grandchild);
        TestTreeResources child = new TestTreeResources(2L, "子节点", 1L, "根节点", 1, grandchildren);
        TestTreeResources sibling = new TestTreeResources(4L, "另一子节点", 1L, "根节点", 1, List.of());
        List<TestTreeResources> children = List.of(child, sibling);
        TestTreeResources root = new TestTreeResources(1L, "根节点", null, null, 0, children);

        List<TreeResources<Long>> result = MybatisTreeObjectHelper.flattenTree(root);

        Assertions.assertEquals(List.of(1L, 2L, 3L, 4L), result.stream().map(TreeResources::getId).toList());
        List<TestTreeResources> original = List.of(root, child, grandchild, sibling);
        for (int i = 0; i < result.size(); i++) {
            TreeResources<Long> actual = result.get(i);
            TestTreeResources expected = original.get(i);
            Assertions.assertNotSame(expected, actual);
            Assertions.assertEquals(expected.getClass(), actual.getClass());
            Assertions.assertEquals(expected.getName(), actual.getName());
            Assertions.assertEquals(expected.getParentId(), actual.getParentId());
            Assertions.assertEquals(expected.getParentName(), actual.getParentName());
            Assertions.assertEquals(expected.getLevel(), actual.getLevel());
            Assertions.assertNull(actual.getChildren());
        }
        Assertions.assertSame(children, root.getChildren());
        Assertions.assertSame(grandchildren, child.getChildren());
        Assertions.assertNull(grandchild.getChildren());
        Assertions.assertTrue(sibling.getChildren().isEmpty());
    }

    @Test
    void testFlattenTreeWithSingleNode() {
        TestTreeResources root = new TestTreeResources(1L, "根节点", null, null, 0, null);

        List<TreeResources<Long>> result = MybatisTreeObjectHelper.flattenTree(root);

        Assertions.assertEquals(1, result.size());
        Assertions.assertNotSame(root, result.getFirst());
        Assertions.assertEquals(root.getId(), result.getFirst().getId());
        Assertions.assertNull(result.getFirst().getChildren());
    }

    @Test
    void testFlattenTreeCopiesMutableAttributes() {
        TestTreeResources child = new TestTreeResources(2L, "子节点", 1L, "根节点", 1, null);
        child.getTags().add("原标签");
        TestTreeResources root = new TestTreeResources(1L, "根节点", null, null, 0, List.of(child));

        List<TreeResources<Long>> result = MybatisTreeObjectHelper.flattenTree(root);
        TestTreeResources copiedChild = (TestTreeResources) result.get(1);

        Assertions.assertEquals(List.of("原标签"), copiedChild.getTags());
        copiedChild.getTags().add("新增标签");
        Assertions.assertEquals(List.of("原标签"), child.getTags());
        Assertions.assertSame(child, root.getChildren().getFirst());
    }

    @Getter
    @AllArgsConstructor
    private static class TestTreeResources implements TreeResources<Long> {

        private final Long id;

        private final String name;

        private final Long parentId;

        private final String parentName;

        private final Integer level;

        private List<TestTreeResources> children;

        private final List<String> tags = new ArrayList<>();
    }
}
