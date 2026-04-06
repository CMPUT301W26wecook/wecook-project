package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.wecookproject.model.User;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListElementAdapterTest {

    private List<User> itemList;
    private ListElementAdapter<User> adapter;

    @Before
    public void setUp() {
        itemList = new ArrayList<>();
        Map<String, Boolean> roles = new HashMap<>();
        roles.put("entrant", true);
        itemList.add(new User("Addr1", "", "id1", "1990-01-01", "City1", "Country1", "First1", "Last1", "Post1", true, new HashMap<>(roles)));
        itemList.add(new User("Addr2", "", "id2", "1991-01-01", "City2", "Country2", "First2", "Last2", "Post2", true, new HashMap<>(roles)));

        adapter = new ListElementAdapter<>(itemList, null);
    }

    @Test
    public void testGetItemCount() {
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void testInitialSelectedList() {
        List<Boolean> selectedList = adapter.getSelectedList();
        assertNotNull(selectedList);
        assertEquals(2, selectedList.size());
        assertFalse(selectedList.get(0));
        assertFalse(selectedList.get(1));
    }

    @Test
    public void testSelectedListUpdates() {
        List<Boolean> selectedList = adapter.getSelectedList();
        selectedList.set(0, true);
        assertTrue(adapter.getSelectedList().get(0));
        assertFalse(adapter.getSelectedList().get(1));
    }
}
