package com.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.example.adapter.ArchiveAdapter;
import com.example.db.DatabaseHelper;
import com.example.model.Gam3ya;
import com.example.util.NavBarHelper;
import com.example.util.ThemeManager;
import com.example.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class ArchiveActivity extends Activity {

    private DatabaseHelper dbHelper;
    private ListView lvArchive;
    private LinearLayout layoutEmptyArchive;
    private final List<Gam3ya> archivedList = new ArrayList<>();
    private ArchiveAdapter adapter;
    private boolean appliedDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedDarkMode = ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive);
        UiUtils.setupRtlAndInsets(this, findViewById(R.id.rootArchive));

        NavBarHelper.setupHeader(this, getString(R.string.archive_title), true);
        NavBarHelper.setupBottomNav(this, R.id.navArchive);

        dbHelper = new DatabaseHelper(this);
        lvArchive = findViewById(R.id.lvArchive);
        layoutEmptyArchive = findViewById(R.id.layoutEmptyArchive);

        adapter = new ArchiveAdapter(this, archivedList);
        lvArchive.setAdapter(adapter);

        lvArchive.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Gam3ya selected = archivedList.get(position);
                Intent intent = new Intent(ArchiveActivity.this, Gam3yaDetailsActivity.class);
                intent.putExtra(Gam3yaDetailsActivity.EXTRA_GAM3YA_ID, selected.id);
                intent.putExtra(Gam3yaDetailsActivity.EXTRA_READ_ONLY, true);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedDarkMode != ThemeManager.isDarkMode(this)) {
            recreate();
            return;
        }
        loadArchive();
    }

    private void loadArchive() {
        List<Gam3ya> loaded = dbHelper.getArchivedGam3yat();
        archivedList.clear();
        archivedList.addAll(loaded);
        adapter.notifyDataSetChanged();

        if (archivedList.isEmpty()) {
            lvArchive.setVisibility(View.GONE);
            layoutEmptyArchive.setVisibility(View.VISIBLE);
        } else {
            lvArchive.setVisibility(View.VISIBLE);
            layoutEmptyArchive.setVisibility(View.GONE);
        }
    }
}
